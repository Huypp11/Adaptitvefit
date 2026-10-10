package com.adaptivefit.dto;

import com.adaptivefit.entity.WorkoutSession;
import com.adaptivefit.util.TextLists;

import java.time.LocalDateTime;
import java.util.List;

public record WorkoutResponse(
        Long id, String status, String interventionCode, LocalDateTime startedAt,
        LocalDateTime finishedAt, List<Item> exercises
) {
    public record Item(Long exerciseId, String name, Integer orderNo, Integer plannedMin,
                       String intensity, String instructions, Double met,
                       List<String> steps, List<String> media, String tips) {}

    public static WorkoutResponse from(WorkoutSession s) {
        List<Item> items = s.getExercises().stream()
                .map(x -> new Item(x.getExercise().getId(), x.getExercise().getName(), x.getOrderNo(),
                        x.getPlannedMin(), x.getExercise().getIntensity(), x.getExercise().getInstructions(),
                        x.getExercise().getMet(), TextLists.split(x.getExercise().getSteps()),
                        TextLists.split(x.getExercise().getMediaUrls()), x.getExercise().getTips()))
                .toList();
        return new WorkoutResponse(s.getId(), s.getStatus().name(), s.getInterventionCode(),
                s.getStartedAt(), s.getFinishedAt(), items);
    }
}
