package com.adaptivefit.dto;

import com.adaptivefit.entity.Exercise;
import com.adaptivefit.util.TextLists;

import java.util.List;

public record ExerciseResponse(
        Long id, String name, String description, Integer durationMin, Integer durationMax,
        Integer difficulty, String intensity, String equipment, String location, String muscleGroup,
        String goal, Integer energyRequirement, Boolean recoverySuitable, Double met,
        List<String> steps, List<String> media, String tips, String instructions,
        Boolean active, List<String> tags
) {
    public static ExerciseResponse from(Exercise e) {
        return new ExerciseResponse(e.getId(), e.getName(), e.getDescription(), e.getDurationMin(),
                e.getDurationMax(), e.getDifficulty(), e.getIntensity(), e.getEquipment(), e.getLocation(),
                e.getMuscleGroup(), e.getGoal(), e.getEnergyRequirement(), e.getRecoverySuitable(), e.getMet(),
                TextLists.split(e.getSteps()), TextLists.split(e.getMediaUrls()), e.getTips(), e.getInstructions(),
                e.getActive(), e.getTags().stream().sorted().toList());
    }
}
