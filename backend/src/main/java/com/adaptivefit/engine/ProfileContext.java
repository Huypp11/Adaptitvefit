package com.adaptivefit.engine;

public record ProfileContext(String fitnessLevel, String goal, String location, String equipment, String preferredWorkout,
                             String maxExerciseIntensity) {
    public ProfileContext(String fitnessLevel, String goal, String location, String equipment, String preferredWorkout) {
        this(fitnessLevel, goal, location, equipment, preferredWorkout, "MEDIUM");
    }

    public ProfileContext {
        if (maxExerciseIntensity == null) maxExerciseIntensity = "MEDIUM";
    }
}
