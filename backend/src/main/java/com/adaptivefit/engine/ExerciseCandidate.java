package com.adaptivefit.engine;

import java.util.Set;

public record ExerciseCandidate(long id, String name, int durationMin, int durationMax, int difficulty,
                                String intensity, String equipment, String location, String muscleGroup,
                                String goal, int energyRequirement, boolean recoverySuitable, Set<String> tags) {}
