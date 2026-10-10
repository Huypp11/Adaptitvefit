package com.adaptivefit.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProfileRequest(
        @NotNull @Min(13) @Max(100) Integer age,
        @NotNull @DecimalMin("100.0") @DecimalMax("250.0") BigDecimal heightCm,
        @NotNull @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal weightKg,
        @NotBlank @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED") String fitnessLevel,
        @NotBlank @Pattern(regexp = "LOSE_WEIGHT|BUILD_MUSCLE|STAY_ACTIVE|ENDURANCE") String goal,
        @NotNull @Min(5) @Max(240) Integer availableMinutes,
        @Size(max = 50) String preferredWorkout,
        @NotBlank @Pattern(regexp = "HOME|GYM|OUTDOOR") String location,
        @Size(max = 255) String equipment
) {}
