package com.adaptivefit.dto;

import jakarta.validation.constraints.*;

public record FeedbackRequest(
        @NotNull Long sessionId,
        @NotNull Boolean completed,
        @Min(1) @Max(5) Integer difficulty,
        @Min(1) @Max(5) Integer fatigue,
        @Min(1) @Max(5) Integer satisfaction,
        Boolean liked,
        @Pattern(regexp = "NO_TIME|TOO_TIRED|TOO_DIFFICULT|DISLIKE|UNEXPECTED_WORK|OTHER") String notCompletedReason
) {}
