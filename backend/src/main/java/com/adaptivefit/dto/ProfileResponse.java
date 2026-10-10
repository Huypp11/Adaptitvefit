package com.adaptivefit.dto;

import com.adaptivefit.entity.UserProfile;

import java.math.BigDecimal;

public record ProfileResponse(
        Integer age, BigDecimal heightCm, BigDecimal weightKg, String fitnessLevel,
        String goal, Integer availableMinutes, String preferredWorkout,
        String location, String equipment
) {
    public static ProfileResponse from(UserProfile p) {
        return new ProfileResponse(p.getAge(), p.getHeightCm(), p.getWeightKg(), p.getFitnessLevel(),
                p.getGoal(), p.getAvailableMinutes(), p.getPreferredWorkout(),
                p.getLocation(), p.getEquipment());
    }
}
