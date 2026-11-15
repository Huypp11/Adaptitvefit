package com.adaptivefit.dto;

import jakarta.validation.constraints.Pattern;

public record AbandonWorkoutRequest(
        @Pattern(regexp = "TOO_TIRED|NO_EQUIPMENT|UNEXPECTED_WORK") String reason
) {}
