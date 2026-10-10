package com.adaptivefit.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkoutStartRequest(
        @NotEmpty @Size(max = 10) List<@NotNull Long> exerciseIds
) {}
