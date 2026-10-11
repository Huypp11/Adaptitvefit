package com.adaptivefit.service;

import com.adaptivefit.dto.RecommendationResponse;
import com.adaptivefit.engine.ExerciseCandidate;
import com.adaptivefit.engine.InterventionEngine;
import com.adaptivefit.engine.ProfileContext;
import com.adaptivefit.engine.RecommendationResult;
import com.adaptivefit.engine.RecommendationScorer;
import com.adaptivefit.engine.StrategyDecision;
import com.adaptivefit.engine.UserState;
import com.adaptivefit.engine.Weights;
import com.adaptivefit.entity.DailyCheckin;
import com.adaptivefit.entity.Exercise;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.UserProfile;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.DailyCheckinRepository;
import com.adaptivefit.repository.ExerciseRepository;
import com.adaptivefit.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class RecommendationService {

    private static final int TOP_N = 3;

    private final CurrentUserService currentUser;
    private final DailyCheckinRepository checkinRepository;
    private final UserProfileRepository profileRepository;
    private final ExerciseRepository exerciseRepository;
    private final InterventionService interventionService;
    private final UserStateService userStateService;
    private final InterventionEngine engine = new InterventionEngine();
    private final RecommendationScorer scorer;

    public RecommendationService(CurrentUserService currentUser, DailyCheckinRepository checkinRepository,
                                 UserProfileRepository profileRepository, ExerciseRepository exerciseRepository,
                                 InterventionService interventionService, UserStateService userStateService,
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
        this.scorer = new RecommendationScorer(new Weights(wTime, wEnergy, wGoal, wDifficulty, wPreference, wRecovery));
    }

    @Transactional(readOnly = true)
    public RecommendationResponse today() {
        User user = currentUser.get();
        UserProfile profile = profileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException("Hãy hoàn thiện hồ sơ trước để nhận gợi ý"));
        DailyCheckin checkin = checkinRepository.findByUserIdAndCheckinDate(user.getId(), LocalDate.now())
                .orElseThrow(() -> new NotFoundException("Hôm nay bạn chưa check-in"));

        UserState state = userStateService.build(user, checkin);
        StrategyDecision decision = engine.decide(state, interventionService.activeRules());

        List<ExerciseCandidate> candidates = exerciseRepository.findByActiveTrue().stream().map(this::toCandidate).toList();
        ProfileContext context = new ProfileContext(profile.getFitnessLevel(), profile.getGoal(), profile.getLocation(),
                profile.getEquipment(), profile.getPreferredWorkout());
        RecommendationResult result = scorer.recommend(state, context, decision, candidates, TOP_N);

        return new RecommendationResponse(
                new RecommendationResponse.Strategy(decision.code(), decision.description(), decision.reasons()),
                new RecommendationResponse.State(state.sleepHours(), state.stress(), state.energy(), state.freeTimeMin(),
                        state.missedDays(), state.workoutFrequency7d(), state.riskLevel(), state.riskSource()),
                result);
    }

    /** Ma chien luoc hien tai de ghi vao buoi tap; null neu chua du du lieu (chua check-in hoac chua co ho so). */
    @Transactional(readOnly = true)
    public String strategyCodeFor(User user) {
        DailyCheckin checkin = checkinRepository.findByUserIdAndCheckinDate(user.getId(), LocalDate.now()).orElse(null);
        if (checkin == null) return null;
        UserState state = userStateService.build(user, checkin);
        return engine.decide(state, interventionService.activeRules()).code();
    }

    private ExerciseCandidate toCandidate(Exercise e) {
        return new ExerciseCandidate(e.getId(), e.getName(), e.getDurationMin(), e.getDurationMax(), e.getDifficulty(),
                e.getIntensity(), e.getEquipment(), e.getLocation(), e.getMuscleGroup(), e.getGoal(),
                e.getEnergyRequirement(), Boolean.TRUE.equals(e.getRecoverySuitable()), Set.copyOf(e.getTags()));
    }
}
