package com.adaptivefit.dto;

import com.adaptivefit.entity.Feedback;

import java.time.LocalDateTime;

public record FeedbackResponse(Long id, Long sessionId, Boolean completed, Integer difficulty, Integer fatigue,
                               Integer satisfaction, Boolean liked, String notCompletedReason,
                               LocalDateTime createdAt) {
    public static FeedbackResponse from(Feedback f) {
        return new FeedbackResponse(f.getId(), f.getSession().getId(), f.getCompleted(), f.getDifficulty(),
                f.getFatigue(), f.getSatisfaction(), f.getLiked(), f.getNotCompletedReason(), f.getCreatedAt());
    }
}
