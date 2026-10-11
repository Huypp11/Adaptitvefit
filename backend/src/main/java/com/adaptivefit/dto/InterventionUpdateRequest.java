package com.adaptivefit.dto;

import jakarta.validation.constraints.*;

public record InterventionUpdateRequest(
        @NotNull @Min(1) @Max(100) Integer priority,
        @NotBlank @Size(max = 500) String conditionExpr,
        @Size(max = 500) String paramsExpr,
        @Size(max = 500) String description,
        @NotNull Boolean active
) {}
