package com.adaptivefit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecommendationEventRequest(
        @NotNull EventType eventType,
        Long recommendationId,
        @NotBlank @Size(max = 80) String trackingId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9:_-]{1,191}") String idempotencyKey,
        @NotNull @Pattern(regexp = "CHECKIN|PROFILE_FALLBACK") String recommendationMode
) {
    public enum EventType { DISPLAYED, SKIPPED }
}
