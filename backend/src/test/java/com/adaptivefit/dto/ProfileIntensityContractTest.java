package com.adaptivefit.dto;

import com.adaptivefit.entity.UserProfile;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProfileIntensityContractTest {

    @Test
    void legacyAndNullIntensityDefaultToMedium() {
        ProfileRequest legacy = request("MEDIUM");
        ProfileRequest missing = new ProfileRequest(legacy.age(), legacy.heightCm(), legacy.weightKg(),
                legacy.fitnessLevel(), legacy.goal(), legacy.availableMinutes(), legacy.preferredWorkout(),
                legacy.location(), legacy.equipment(), null);
        ProfileRequest oldConstructor = new ProfileRequest(legacy.age(), legacy.heightCm(), legacy.weightKg(),
                legacy.fitnessLevel(), legacy.goal(), legacy.availableMinutes(), legacy.preferredWorkout(),
                legacy.location(), legacy.equipment());

        assertEquals("MEDIUM", missing.maxExerciseIntensity());
        assertEquals("MEDIUM", oldConstructor.maxExerciseIntensity());

        UserProfile existingProfile = new UserProfile();
        assertEquals("MEDIUM", ProfileResponse.from(existingProfile).maxExerciseIntensity());
    }

    @Test
    void onlySupportedIntensityValuesPassRequestValidation() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            assertFalse(validator.validate(request("LOW")).size() > 0);
            assertFalse(validator.validate(request("MEDIUM")).size() > 0);
            assertFalse(validator.validate(request("HIGH")).size() > 0);

            ProfileRequest invalid = request("EXTREME");
            assertEquals(1, validator.validateProperty(invalid, "maxExerciseIntensity").size());
        }
    }

    private static ProfileRequest request(String maxIntensity) {
        return new ProfileRequest(25, new BigDecimal("170.0"), new BigDecimal("65.0"),
                "BEGINNER", "STAY_ACTIVE", 30, null, "HOME", null, maxIntensity);
    }
}
