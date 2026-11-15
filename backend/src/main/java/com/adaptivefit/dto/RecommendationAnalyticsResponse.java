package com.adaptivefit.dto;

public record RecommendationAnalyticsResponse(
        int windowDays,
        long usersWithCurrentConsent,
        long displayedCheckinJourneys,
        long displayedFallbackJourneys,
        long matureJourneys,
        long completedValidWithin24Hours,
        Double completionRateWithin24Hours,
        long matureCheckinJourneys,
        long completedCheckinWithin24Hours,
        Double completionRateCheckinWithin24Hours,
        long matureFallbackJourneys,
        long completedFallbackWithin24Hours,
        Double completionRateFallbackWithin24Hours,
        long skippedJourneys,
        long startedJourneys,
        long abandonedJourneys,
        long invalidWorkoutJourneys,
        long journeysWithoutFeedback,
        long journeysStillWithinObservationWindow,
        long lifecycleEventsWithoutStart,
        long journeysWithoutCompletionEvent,
        String note
) {}
