package com.adaptivefit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecommendationDataConsentRequest(
        @NotNull Boolean granted,
        @NotBlank @Size(max = 40) String policyVersion
) {}
