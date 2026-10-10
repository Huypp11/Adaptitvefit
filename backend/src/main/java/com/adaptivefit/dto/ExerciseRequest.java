package com.adaptivefit.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record ExerciseRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull @Min(1) @Max(240) Integer durationMin,
        @NotNull @Min(1) @Max(240) Integer durationMax,
        @NotNull @Min(1) @Max(5) Integer difficulty,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH") String intensity,
        @Size(max = 255) String equipment,
        @NotBlank @Pattern(regexp = "HOME|GYM|OUTDOOR|ANY") String location,
        @Size(max = 50) String muscleGroup,
        @Pattern(regexp = "LOSE_WEIGHT|BUILD_MUSCLE|STAY_ACTIVE|ENDURANCE|RECOVERY") String goal,
        @NotNull @Min(1) @Max(5) Integer energyRequirement,
        @NotNull Boolean recoverySuitable,
        @DecimalMin(value = "1.0", message = "MET tối thiểu là 1.0") @DecimalMax(value = "20.0", message = "MET tối đa là 20.0") BigDecimal met,
        @Size(max = 15, message = "Tối đa 15 bước") List<@NotBlank @Size(max = 300, message = "Mỗi bước tối đa 300 ký tự") String> steps,
        @Size(max = 5, message = "Tối đa 5 ảnh") List<@NotBlank @Size(max = 190) String> media,
        @Size(max = 1000) String tips,
        @Size(max = 2000) String instructions,
        Boolean active,
        @Size(max = 10) List<@Pattern(regexp = "[a-z0-9_]{1,40}", message = "Tag chỉ gồm a-z, 0-9, _") String> tags
) {}
