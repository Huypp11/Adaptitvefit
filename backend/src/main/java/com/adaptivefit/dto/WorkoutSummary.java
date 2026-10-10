package com.adaptivefit.dto;

import com.adaptivefit.entity.WorkoutSession;

import java.time.LocalDateTime;

public record WorkoutSummary(Long id, String status, String interventionCode,
                             LocalDateTime startedAt, LocalDateTime finishedAt) {
    public static WorkoutSummary from(WorkoutSession s) {
        return new WorkoutSummary(s.getId(), s.getStatus().name(), s.getInterventionCode(),
                s.getStartedAt(), s.getFinishedAt());
    }
}
