package com.adaptivefit.dto;

import java.time.LocalDateTime;

public record RecommendationDataConsentResponse(
        boolean trackingEnabled,
        String policyVersion,
        LocalDateTime changedAt,
        long storedEventCount
) {}
