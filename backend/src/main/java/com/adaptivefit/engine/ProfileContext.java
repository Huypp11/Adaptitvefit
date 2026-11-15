package com.adaptivefit.engine;

public record ProfileContext(String fitnessLevel, String goal, String location, String equipment, String preferredWorkout,
                             String maxExerciseIntensity, String recentAbandonmentReason) {
    public ProfileContext(String fitnessLevel, String goal, String location, String equipment, String preferredWorkout) {
        this(fitnessLevel, goal, location, equipment, preferredWorkout, "MEDIUM", null);
    }

    public ProfileContext(String fitnessLevel, String goal, String location, String equipment, String preferredWorkout,
                          String maxExerciseIntensity) {
        this(fitnessLevel, goal, location, equipment, preferredWorkout, maxExerciseIntensity, null);
    }

    public ProfileContext {
        if (maxExerciseIntensity == null) maxExerciseIntensity = "MEDIUM";
    }
}
