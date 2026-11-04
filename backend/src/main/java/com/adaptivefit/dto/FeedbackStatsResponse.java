package com.adaptivefit.dto;

import java.util.Map;

public record FeedbackStatsResponse(
        long recommendationsSaved,
        long recommendationsStarted,
        long workoutSessionsCompleted,
        Map<String, Long> feedbackByStatus,
        Map<String, Long> reasonsNotDone,
        Double averageSuitabilityRating,
        double startRate,
        Double workoutCompletionRate
) {}
