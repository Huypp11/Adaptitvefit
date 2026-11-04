package com.adaptivefit.dto;

import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.RecommendationFeedbackStatus;

import java.time.LocalDateTime;

public record FeedbackResponse(Long id, Long sessionId, Boolean completed, Integer difficulty, Integer fatigue,
                               Integer satisfaction, Boolean liked, String notCompletedReason,
                               LocalDateTime createdAt, LocalDateTime updatedAt, Long recommendationId,
                               RecommendationFeedbackStatus recommendationStatus, Integer suitabilityRating,
                               Integer recommendationDifficulty, String recommendationReason,
                               String recommendationComment) {
    public static FeedbackResponse from(Feedback f) {
        return new FeedbackResponse(f.getId(), f.getSession() == null ? null : f.getSession().getId(), f.getCompleted(), f.getDifficulty(),
                f.getFatigue(), f.getSatisfaction(), f.getLiked(), f.getNotCompletedReason(),
                f.getCreatedAt(), f.getUpdatedAt(),
                f.getRecommendation() == null ? null : f.getRecommendation().getId(),
                f.getRecommendationStatus(), f.getSuitabilityRating(), f.getRecommendationDifficulty(),
                f.getRecommendationReason(), f.getRecommendationComment());
    }
}
