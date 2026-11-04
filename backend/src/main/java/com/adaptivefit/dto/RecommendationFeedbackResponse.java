package com.adaptivefit.dto;

import com.adaptivefit.entity.Feedback;
import com.adaptivefit.entity.RecommendationFeedbackStatus;

import java.time.LocalDateTime;

public record RecommendationFeedbackResponse(
        Long id,
        Long recommendationId,
        RecommendationFeedbackStatus status,
        Integer suitabilityRating,
        Integer difficultyRating,
        String reason,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RecommendationFeedbackResponse from(Feedback feedback) {
        return new RecommendationFeedbackResponse(feedback.getId(), feedback.getRecommendation().getId(),
                feedback.getRecommendationStatus(), feedback.getSuitabilityRating(),
                feedback.getRecommendationDifficulty(), feedback.getRecommendationReason(),
                feedback.getRecommendationComment(), feedback.getCreatedAt(), feedback.getUpdatedAt());
    }
}
