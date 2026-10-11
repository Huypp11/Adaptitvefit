package com.adaptivefit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * plannedMinutes (tuy chon): thoi luong cho tung bai, cung thu tu voi exerciseIds.
 * followRecommendation (tuy chon): neu true, server tu tinh chien luoc hien tai de ghi vao buoi tap.
 */
public record WorkoutStartRequest(
        @NotEmpty @Size(max = 10) List<@NotNull Long> exerciseIds,
        @Size(max = 10) List<@NotNull @Min(1) @Max(240) Integer> plannedMinutes,
        Boolean followRecommendation
) {}
