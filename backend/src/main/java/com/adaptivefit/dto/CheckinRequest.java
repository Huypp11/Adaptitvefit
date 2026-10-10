package com.adaptivefit.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CheckinRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("14.0") BigDecimal sleepHours,
        @NotNull @Min(1) @Max(5) Integer stress,
        @NotNull @Min(1) @Max(5) Integer energy,
        @NotNull @Min(1) @Max(5) Integer mood,
        @NotNull @Min(0) @Max(480) Integer freeTimeMin,
        @NotNull @Min(1) @Max(5) Integer motivation,
        @NotNull Boolean exercisedToday,
        @Min(1) @Max(300) Integer durationMin,
        @Min(1) @Max(5) Integer difficulty,
        @Pattern(regexp = "NO_TIME|TOO_TIRED|TOO_DIFFICULT|DISLIKE|UNEXPECTED_WORK|OTHER") String missedReason
) {}
