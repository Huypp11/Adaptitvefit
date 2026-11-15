package com.adaptivefit.service;

import com.adaptivefit.dto.PageResponse;
import com.adaptivefit.dto.RecommendationFeedbackResponse;
import com.adaptivefit.dto.RecommendationResponse;
import com.adaptivefit.dto.RecommendationSnapshot;
import com.adaptivefit.dto.RiskInfo;
import com.adaptivefit.engine.ExerciseCandidate;
import com.adaptivefit.engine.InterventionEngine;
import com.adaptivefit.engine.ProfileContext;
import com.adaptivefit.engine.RuleDef;
import com.adaptivefit.engine.RecommendationResult;
import com.adaptivefit.engine.RecommendationScorer;
import com.adaptivefit.engine.StrategyDecision;
import com.adaptivefit.engine.StrategyParams;
import com.adaptivefit.engine.UserState;
import com.adaptivefit.engine.Weights;
import com.adaptivefit.entity.DailyCheckin;
import com.adaptivefit.entity.Exercise;
import com.adaptivefit.entity.RecommendationHistory;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.UserProfile;
import com.adaptivefit.entity.WorkoutStatus;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.observability.PerformanceTiming;
import com.adaptivefit.repository.DailyCheckinRepository;
import com.adaptivefit.repository.ExerciseRepository;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.RecommendationHistoryRepository;
import com.adaptivefit.repository.UserProfileRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendationService {

    private static final int TOP_N = 3;

    /** Chien luoc luc bat dau buoi tap va du doan lien quan (de truy vet ve sau). */
    public record StrategyContext(String code, Long predictionId, RecommendationHistory recommendation,
                                  boolean selectionChanged) {}

    private final CurrentUserService currentUser;
    private final DailyCheckinRepository checkinRepository;
    private final UserProfileRepository profileRepository;
    private final ExerciseRepository exerciseRepository;
    private final InterventionService interventionService;
    private final UserStateService userStateService;
    private final RiskService riskService;
    private final RecommendationHistoryRepository recommendationRepository;
    private final WorkoutSessionRepository workoutRepository;
    private final FeedbackRepository feedbackRepository;
    private final ObjectMapper mapper;
    private final InterventionEngine engine = new InterventionEngine();
    private final RecommendationScorer scorer;

    public RecommendationService(CurrentUserService currentUser, DailyCheckinRepository checkinRepository,
                                 UserProfileRepository profileRepository, ExerciseRepository exerciseRepository,
                                 InterventionService interventionService, UserStateService userStateService,
                                 RiskService riskService, RecommendationHistoryRepository recommendationRepository,
                                 WorkoutSessionRepository workoutRepository, FeedbackRepository feedbackRepository,
                                 ObjectMapper mapper,
                                 @Value("${app.recommendation.weights.time:0.25}") double wTime,
                                 @Value("${app.recommendation.weights.energy:0.20}") double wEnergy,
                                 @Value("${app.recommendation.weights.goal:0.20}") double wGoal,
                                 @Value("${app.recommendation.weights.difficulty:0.15}") double wDifficulty,
                                 @Value("${app.recommendation.weights.preference:0.10}") double wPreference,
                                 @Value("${app.recommendation.weights.recovery:0.10}") double wRecovery) {
        this.currentUser = currentUser;
        this.checkinRepository = checkinRepository;
        this.profileRepository = profileRepository;
        this.exerciseRepository = exerciseRepository;
        this.interventionService = interventionService;
        this.userStateService = userStateService;
        this.riskService = riskService;
        this.recommendationRepository = recommendationRepository;
        this.workoutRepository = workoutRepository;
        this.feedbackRepository = feedbackRepository;
        this.mapper = mapper;
        this.scorer = new RecommendationScorer(new Weights(wTime, wEnergy, wGoal, wDifficulty, wPreference, wRecovery));
    }

    @Transactional
    public RecommendationResponse today() {
        return PerformanceTiming.measure("recommendation.service", this::buildToday);
    }

    private RecommendationResponse buildToday() {
        User user = PerformanceTiming.measure("recommendation.current_user", currentUser::get);
        PerformanceTiming.setUserId(user.getId());
        UserProfile profile = PerformanceTiming.measure("recommendation.profile_query",
                () -> profileRepository.findByUserId(user.getId()))
                .orElseThrow(() -> new BadRequestException("Hãy hoàn thiện hồ sơ trước để nhận gợi ý"));
        DailyCheckin checkin = PerformanceTiming.measure("recommendation.checkin_lock_query",
                () -> checkinRepository.findByUserIdAndCheckinDateForUpdate(user.getId(), LocalDate.now()))
                .orElse(null);
        if (checkin == null) return buildProfileFallback(user, profile);
        RecommendationHistory existing = PerformanceTiming.measure("recommendation.snapshot_lock_query",
                () -> recommendationRepository.findForUpdate(checkin.getId(), user.getId()))
                .orElse(null);
        if (existing != null) {
            PerformanceTiming.event("recommendation.snapshot_cache", "result=hit");
            return PerformanceTiming.measure("recommendation.response_mapping", () -> toResponse(existing));
        }
        PerformanceTiming.event("recommendation.snapshot_cache", "result=miss");

        UserStateService.Snapshot snap = userStateService.snapshot(user, checkin);
        RiskInfo risk = riskService.assessForRecommendation(
                checkin, snap.features(), snap.state().riskLevel());
        UserState state = withRisk(snap.state(), risk);
        List<RuleDef> activeRules = PerformanceTiming.measure("recommendation.active_rules_query",
                interventionService::activeRules);
        StrategyDecision decision = PerformanceTiming.measure("intervention_engine.decide",
                () -> decideForRisk(state, risk, activeRules, profile.getMaxExerciseIntensity()));

        List<ExerciseCandidate> candidates = PerformanceTiming.measure("recommendation.active_exercises_query_and_map",
                () -> exerciseRepository.findByActiveTrue().stream().map(this::toCandidate).toList());
        ProfileContext context = new ProfileContext(profile.getFitnessLevel(), profile.getGoal(), profile.getLocation(),
                profile.getEquipment(), profile.getPreferredWorkout(),
                profile.getMaxExerciseIntensity() == null ? "MEDIUM" : profile.getMaxExerciseIntensity(),
                recentAbandonmentReason(user));
        RecommendationResult result = PerformanceTiming.measure("recommendation.scorer",
                () -> scorer.recommend(state, context, decision, candidates, TOP_N));

        RecommendationSnapshot snapshot = new RecommendationSnapshot(
                new RecommendationResponse.Strategy(decision.code(), decision.description(), decision.reasons()),
                new RecommendationResponse.State(state.sleepHours(), state.stress(), state.energy(), state.freeTimeMin(),
                        state.missedDays(), state.workoutFrequency7d(), state.riskLevel(), state.riskSource(),
                        state.exercisedToday(), Boolean.TRUE.equals(checkin.getExercisedToday()) ? checkin.getDurationMin() : null),
                risk, result);
        RecommendationHistory history = new RecommendationHistory();
        history.setUser(user);
        history.setCheckin(checkin);
        history.setRiskPredictionId(risk.predictionId());
        history.setSnapshotJson(PerformanceTiming.measure("recommendation.snapshot_serialize",
                () -> writeSnapshot(snapshot)));
        history.setCreatedAt(LocalDateTime.now());
        RecommendationHistory saved = PerformanceTiming.measure("recommendation.snapshot_persist",
                () -> recommendationRepository.saveAndFlush(history));
        return PerformanceTiming.measure("recommendation.response_mapping", () -> toResponse(saved));
    }

    private RecommendationResponse buildProfileFallback(User user, UserProfile profile) {
        int availableMinutes = profile.getAvailableMinutes() == null ? 30 : profile.getAvailableMinutes();
        UserState state = userStateService.profileFallback(user, availableMinutes);
        List<ExerciseCandidate> candidates = PerformanceTiming.measure("recommendation.active_exercises_query_and_map",
                () -> exerciseRepository.findByActiveTrue().stream().map(this::toCandidate).toList());
        ProfileContext context = new ProfileContext(profile.getFitnessLevel(), profile.getGoal(), profile.getLocation(),
                profile.getEquipment(), profile.getPreferredWorkout(),
                profile.getMaxExerciseIntensity() == null ? "MEDIUM" : profile.getMaxExerciseIntensity(),
                recentAbandonmentReason(user));
        StrategyDecision normal = new StrategyDecision("NORMAL", "Bài tập mặc định dựa trên hồ sơ",
                List.of("Chưa có check-in hôm nay; dùng hồ sơ và lịch sử hoạt động gần đây"), StrategyParams.none());
        RecommendationResult result = PerformanceTiming.measure("recommendation.scorer",
                () -> scorer.recommend(state, context, normal, candidates, TOP_N));
        return new RecommendationResponse(null, "GENERATED", null, LocalDateTime.now(),
                new RecommendationResponse.Strategy(normal.code(), normal.description(), normal.reasons()),
                null, null, result, "PROFILE_FALLBACK", java.util.UUID.randomUUID().toString());
    }

    /** Danh gia nguy co cua check-in hom nay (diem + giai thich SHAP). Khong can ho so. */
    @Transactional(readOnly = true)
    public RiskInfo todayRisk() {
        User user = PerformanceTiming.measure("ai_today.current_user", currentUser::get);
        PerformanceTiming.setUserId(user.getId());
        DailyCheckin checkin = PerformanceTiming.measure("ai_today.checkin_query", () -> todayCheckin(user));
        UserStateService.Snapshot snap = userStateService.snapshot(user, checkin);
        return riskService.assess(checkin, snap.features(), snap.state().riskLevel());
    }

    @Transactional(readOnly = true)
    public PageResponse<RecommendationResponse> history(int page, int size) {
        User user = currentUser.get();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return PageResponse.of(recommendationRepository.findByUserId(user.getId(), pageable).map(this::toResponse));
    }

    /** Chien luoc hien tai de ghi vao buoi tap; null neu hom nay chua check-in. */
    @Transactional(readOnly = true)
    public StrategyContext strategyContextFor(User user) {
        DailyCheckin checkin = checkinRepository.findByUserIdAndCheckinDate(user.getId(), LocalDate.now()).orElse(null);
        if (checkin == null) return null;
        UserStateService.Snapshot snap = userStateService.snapshot(user, checkin);
        RiskInfo risk = riskService.assess(checkin, snap.features(), snap.state().riskLevel());
        StrategyDecision decision = decideForRisk(withRisk(snap.state(), risk), risk, interventionService.activeRules(),
                null);
        return new StrategyContext(decision.code(), risk.predictionId(), null, false);
    }

    @Transactional(readOnly = true)
    public StrategyContext strategyContextFor(User user, Long recommendationId, List<Long> exerciseIds,
                                              List<Integer> plannedMinutes) {
        RecommendationHistory history = recommendationRepository.findByIdAndUserId(recommendationId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy gợi ý"));
        RecommendationSnapshot snapshot = readSnapshot(history);
        Map<Long, Set<Integer>> suggested = new HashMap<>();
        snapshot.recommendation().items().forEach(item ->
                suggested.computeIfAbsent(item.exerciseId(), ignored -> new HashSet<>()).add(item.plannedMin()));
        snapshot.recommendation().plan().forEach(item ->
                suggested.computeIfAbsent(item.exerciseId(), ignored -> new HashSet<>()).add(item.plannedMin()));
        for (int i = 0; i < exerciseIds.size(); i++) {
            Set<Integer> suggestedMinutes = suggested.get(exerciseIds.get(i));
            if (suggestedMinutes == null || (plannedMinutes != null && !suggestedMinutes.contains(plannedMinutes.get(i)))) {
                throw new BadRequestException("Bài tập hoặc thời lượng không thuộc gợi ý đã chọn");
            }
        }
        List<Long> recommendedIds = snapshot.recommendation().plan().stream()
                .map(RecommendationResult.PlanItem::exerciseId).toList();
        boolean selectionChanged = !recommendedIds.equals(exerciseIds);
        if (!selectionChanged && plannedMinutes != null) {
            List<Integer> recommendedMinutes = snapshot.recommendation().plan().stream()
                    .map(RecommendationResult.PlanItem::plannedMin).toList();
            selectionChanged = !recommendedMinutes.equals(plannedMinutes);
        }
        return new StrategyContext(snapshot.strategy().code(), history.getRiskPredictionId(), history, selectionChanged);
    }

    private DailyCheckin todayCheckin(User user) {
        return checkinRepository.findByUserIdAndCheckinDate(user.getId(), LocalDate.now())
                .orElseThrow(() -> new NotFoundException("Hôm nay bạn chưa check-in"));
    }

    private UserState withRisk(UserState s, RiskInfo risk) {
        String level = risk.riskLevel();
        if ("model".equals(risk.source()) && risk.riskScore() != null) {
            level = riskBand(risk.riskScore());
        }
        return new UserState(s.sleepHours(), s.stress(), s.energy(), s.mood(), s.motivation(), s.freeTimeMin(),
                s.exercisedToday(), s.missedDays(), s.workoutFrequency7d(), level, risk.source());
    }

    private StrategyDecision decideForRisk(UserState state, RiskInfo risk, List<RuleDef> rules, String maxIntensity) {
        StrategyDecision decision = engine.decide(state, rules, maxIntensity);
        if (!"model".equals(risk.source()) || risk.riskScore() == null || state.exercisedToday()) return decision;
        String band = riskBand(risk.riskScore());
        if (band.equals("LOW")) return decision;
        boolean fatigueEvidence = state.sleepHours() < 6.0 || state.stress() >= 4 || state.energy() <= 2;
        String code = band.equals("HIGH") && fatigueEvidence ? "RECOVERY" : "REDUCE";
        String explanation = code.equals("RECOVERY")
                ? "Nguy cơ bỏ lỡ buổi tập cao kèm dấu hiệu mệt mỏi từ check-in; ưu tiên phục hồi"
                : "Ưu tiên giảm tải do nguy cơ bỏ lỡ buổi tập";
        return strategyForCode(code, rules, explanation);
    }

    static String riskBand(double score) {
        return score <= 0.30 ? "LOW" : score <= 0.60 ? "MEDIUM" : "HIGH";
    }

    private StrategyDecision strategyForCode(String code, List<RuleDef> rules, String explanation) {
        RuleDef rule = rules.stream().filter(r -> r.active() && r.code().equals(code)).findFirst().orElse(null);
        StrategyParams params = rule == null ? defaultParams(code) : StrategyParams.parse(rule.paramsExpr());
        String description = rule == null ? explanation : rule.description();
        return new StrategyDecision(code, description, List.of(explanation), params);
    }

    private StrategyParams defaultParams(String code) {
        return switch (code) {
            case "RECOVERY" -> new StrategyParams(15, "LOW", null, true, -1.0);
            case "REDUCE" -> new StrategyParams(20, "MEDIUM", null, false, -0.5);
            default -> StrategyParams.none();
        };
    }

    private String recentAbandonmentReason(User user) {
        LocalDate today = LocalDate.now();
        LocalDateTime yesterday = today.minusDays(1).atStartOfDay();
        return feedbackRepository.findRecentAbandonmentReasons(user.getId(), WorkoutStatus.ABANDONED,
                        yesterday, today.atStartOfDay(), PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
    }

    private RecommendationResponse toResponse(RecommendationHistory history) {
        RecommendationSnapshot snapshot = readSnapshot(history);
        var workout = workoutRepository.findByRecommendation_IdAndUser_Id(history.getId(), history.getUser().getId())
                .orElse(null);
        String interactionStatus = workout == null ? "GENERATED" : workout.getStatus().name();
        RecommendationFeedbackResponse feedback = feedbackRepository.findByRecommendation_Id(history.getId())
                .map(RecommendationFeedbackResponse::from)
                .orElse(null);
        return new RecommendationResponse(history.getId(), interactionStatus, feedback, history.getCreatedAt(), snapshot.strategy(),
                snapshot.state(), snapshot.risk(), snapshot.recommendation(), "CHECKIN",
                "recommendation:" + history.getId());
    }

    private String writeSnapshot(RecommendationSnapshot snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không lưu được bản chụp gợi ý", e);
        }
    }

    private RecommendationSnapshot readSnapshot(RecommendationHistory history) {
        try {
            return mapper.readValue(history.getSnapshotJson(), RecommendationSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không đọc được bản chụp gợi ý #" + history.getId(), e);
        }
    }

    private ExerciseCandidate toCandidate(Exercise e) {
        return new ExerciseCandidate(e.getId(), e.getName(), e.getDurationMin(), e.getDurationMax(), e.getDifficulty(),
                e.getIntensity(), e.getEquipment(), e.getLocation(), e.getMuscleGroup(), e.getGoal(),
                e.getEnergyRequirement(), Boolean.TRUE.equals(e.getRecoverySuitable()), Set.copyOf(e.getTags()));
    }
}
