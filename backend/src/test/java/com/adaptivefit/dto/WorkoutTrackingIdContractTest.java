package com.adaptivefit.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkoutTrackingIdContractTest {

    @Test
    void acceptsRecommendationAndFallbackTrackingIds() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            WorkoutStartRequest checkin = request("recommendation:123");
            WorkoutStartRequest fallback = request(UUID.randomUUID().toString());
            WorkoutStartRequest malformed = request("recommendation:not-a-number");

            assertEquals(0, validator.validate(checkin).size());
            assertEquals(0, validator.validate(fallback).size());
            assertEquals(1, validator.validateProperty(malformed, "trackingId").size());
        }
    }

    private static WorkoutStartRequest request(String trackingId) {
        return new WorkoutStartRequest(List.of(1L), List.of(10), true, null, trackingId);
    }
}
