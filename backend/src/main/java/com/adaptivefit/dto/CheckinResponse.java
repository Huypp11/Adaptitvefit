package com.adaptivefit.dto;

import com.adaptivefit.entity.DailyCheckin;

import java.time.LocalDate;

public record CheckinResponse(
        Long id, LocalDate checkinDate, Double sleepHours, Integer stress, Integer energy, Integer mood,
        Integer freeTimeMin, Integer motivation, Boolean exercisedToday, Integer durationMin,
        Integer difficulty, String missedReason
) {
    public static CheckinResponse from(DailyCheckin c) {
        return new CheckinResponse(c.getId(), c.getCheckinDate(), c.getSleepHours(), c.getStress(),
                c.getEnergy(), c.getMood(), c.getFreeTimeMin(), c.getMotivation(), c.getExercisedToday(),
                c.getDurationMin(), c.getDifficulty(), c.getMissedReason());
    }
}
