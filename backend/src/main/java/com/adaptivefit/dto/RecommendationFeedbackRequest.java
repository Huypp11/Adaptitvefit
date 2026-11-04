package com.adaptivefit.dto;

import com.adaptivefit.entity.RecommendationFeedbackStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecommendationFeedbackRequest(
        @NotNull RecommendationFeedbackStatus status,
        @Min(1) @Max(5) Integer suitabilityRating,
        @Min(1) @Max(5) Integer difficultyRating,
        @Pattern(regexp = "NO_TIME|TOO_TIRED|NO_EQUIPMENT|NOT_SUITABLE|OTHER") String reason,
        @Size(max = 500) String comment
) {}
