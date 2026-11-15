package com.adaptivefit.service;

import com.adaptivefit.dto.FeedbackRequest;
import com.adaptivefit.dto.FeedbackResponse;
import com.adaptivefit.dto.FeedbackStatsResponse;
import com.adaptivefit.dto.RecommendationFeedbackRequest;
import com.adaptivefit.dto.RecommendationFeedbackResponse;
import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.RecommendationFeedbackStatus;
import com.adaptivefit.entity.RecommendationHistory;
import com.adaptivefit.entity.User;
import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.entity.WorkoutStatus;
import com.adaptivefit.exception.BadRequestException;
import com.adaptivefit.exception.ConflictException;
import com.adaptivefit.exception.NotFoundException;
import com.adaptivefit.repository.FeedbackRepository;
import com.adaptivefit.repository.RecommendationHistoryRepository;
import com.adaptivefit.repository.WorkoutSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final RecommendationHistoryRepository recommendationRepository;
    private final CurrentUserService currentUser;
    private final RecommendationEventService recommendationEventService;

    @Transactional
    public FeedbackResponse create(FeedbackRequest req) {
        User user = currentUser.get();
        WorkoutSession session = sessionRepository.findByIdAndUserId(req.sessionId(), user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy buổi tập"));

        if (session.getStatus() == WorkoutStatus.STARTED) {
            throw new ConflictException("Hãy kết thúc buổi tập trước khi gửi feedback");
        }
        if (session.getStatus() == WorkoutStatus.INVALID_WORKOUT) {
            throw new BadRequestException("Buổi tập chưa đạt thời lượng tối thiểu nên không ghi nhận feedback");
        }
        if (feedbackRepository.existsBySessionId(session.getId())) {
            throw new ConflictException("Buổi tập này đã có feedback");
        }
        RecommendationHistory recommendation = session.getRecommendation();

        boolean sessionCompleted = session.getStatus() == WorkoutStatus.COMPLETED;
        if (req.completed() != sessionCompleted) {
            throw new BadRequestException("Trường completed không khớp với trạng thái buổi tập");
        }
        if (sessionCompleted) {
            if (req.difficulty() == null || req.fatigue() == null || req.satisfaction() == null) {
                throw new BadRequestException("Cần nhập difficulty, fatigue và satisfaction khi đã hoàn thành");
            }
        } else if (req.notCompletedReason() == null) {
            throw new BadRequestException("Cần nhập notCompletedReason khi không hoàn thành");
        }

        Feedback f = recommendation == null ? new Feedback()
                : feedbackRepository.findByRecommendation_Id(recommendation.getId()).orElseGet(Feedback::new);
        if (f.getSession() != null && !f.getSession().getId().equals(session.getId())) {
            throw new ConflictException("Gợi ý này đã được liên kết với một buổi tập khác");
        }
        f.setSession(session);
        f.setCompleted(req.completed());
        f.setDifficulty(req.difficulty());
        f.setFatigue(req.fatigue());
        f.setSatisfaction(req.satisfaction());
        f.setLiked(req.liked());
        f.setNotCompletedReason(sessionCompleted ? null : req.notCompletedReason());
        f.setUpdatedAt(LocalDateTime.now());
        if (recommendation != null) {
            f.setRecommendation(recommendation);
            if (f.getRecommendationStatus() == null) {
                f.setRecommendationStatus(sessionCompleted
                        ? RecommendationFeedbackStatus.COMPLETED : RecommendationFeedbackStatus.NOT_DONE);
            }
        }
        Feedback saved = feedbackRepository.save(f);
        recommendationEventService.recordFeedbackSubmitted(user, session,
                recommendation == null ? null : recommendation.getId());
        return FeedbackResponse.from(saved);
    }

    @Transactional
    public RecommendationFeedbackResponse createForRecommendation(Long recommendationId,
                                                                  RecommendationFeedbackRequest req) {
        RecommendationHistory recommendation = ownedRecommendation(recommendationId);
        if (feedbackRepository.existsByRecommendation_Id(recommendationId)) {
            throw new ConflictException("Gợi ý này đã có feedback; hãy cập nhật feedback hiện có");
        }
        Feedback feedback = new Feedback();
        feedback.setRecommendation(recommendation);
        applyRecommendationFeedback(feedback, req);
        RecommendationFeedbackResponse response = RecommendationFeedbackResponse.from(feedbackRepository.save(feedback));
        recommendationEventService.recordFeedbackSubmitted(currentUser.get(), null, recommendationId);
        return response;
    }

    @Transactional
    public RecommendationFeedbackResponse updateForRecommendation(Long recommendationId,
                                                                  RecommendationFeedbackRequest req) {
        ownedRecommendation(recommendationId);
        Feedback feedback = feedbackRepository.findByRecommendation_Id(recommendationId)
                .orElseThrow(() -> new NotFoundException("Chưa có feedback cho gợi ý này"));
        applyRecommendationFeedback(feedback, req);
        RecommendationFeedbackResponse response = RecommendationFeedbackResponse.from(feedback);
        recommendationEventService.recordFeedbackSubmitted(currentUser.get(), null, recommendationId);
        return response;
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> myFeedback() {
        return feedbackRepository.findAllForUser(currentUser.get().getId())
                .stream().map(FeedbackResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FeedbackStatsResponse statistics() {
        long saved = recommendationRepository.count();
        long started = sessionRepository.countByRecommendationIsNotNull();
        long completed = sessionRepository.countByRecommendationIsNotNullAndStatus(WorkoutStatus.COMPLETED);
        long eligibleSessions = sessionRepository.countByRecommendationIsNotNullAndStatusNot(WorkoutStatus.INVALID_WORKOUT);

        Map<String, Long> statuses = new LinkedHashMap<>();
        for (RecommendationFeedbackStatus status : RecommendationFeedbackStatus.values()) {
            statuses.put(status.name(), feedbackRepository.countByRecommendationStatus(status));
        }
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (Object[] row : feedbackRepository.countRecommendationReasons()) {
            reasons.put((String) row[0], (Long) row[1]);
        }
        Double completionRate = eligibleSessions == 0 ? null : (double) completed / eligibleSessions;
        return new FeedbackStatsResponse(saved, started, completed, statuses, reasons,
                feedbackRepository.averageRecommendationSuitability(),
                saved == 0 ? 0.0 : (double) started / saved, completionRate);
    }

    private RecommendationHistory ownedRecommendation(Long recommendationId) {
        User user = currentUser.get();
        return recommendationRepository.findByIdAndUserId(recommendationId, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy gợi ý"));
    }

    private void applyRecommendationFeedback(Feedback feedback, RecommendationFeedbackRequest req) {
        feedback.setRecommendationStatus(req.status());
        feedback.setSuitabilityRating(req.suitabilityRating());
        feedback.setRecommendationDifficulty(req.difficultyRating());
        boolean reasonApplies = req.status() == RecommendationFeedbackStatus.NOT_DONE
                || req.status() == RecommendationFeedbackStatus.NOT_SUITABLE;
        feedback.setRecommendationReason(reasonApplies ? req.reason() : null);
        feedback.setRecommendationComment(req.comment() == null || req.comment().isBlank()
                ? null : req.comment().trim());
        feedback.setUpdatedAt(LocalDateTime.now());
    }
}
