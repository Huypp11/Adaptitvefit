package com.adaptivefit.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Trang thai hien tai cua nguoi dung: check-in hom nay + dac trung lich su (cung dinh nghia voi feature cua model). */
public record UserState(
        double sleepHours, int stress, int energy, int mood, int motivation, int freeTimeMin,
        boolean exercisedToday, int missedDays, int workoutFrequency7d,
        String riskLevel, String riskSource) {

    public static final String RISK_SOURCE_HISTORY = "history_rule";

    public Map<String, Double> facts() {
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("sleep_hours", sleepHours);
        f.put("stress_level", (double) stress);
        f.put("energy_level", (double) energy);
        f.put("mood", (double) mood);
        f.put("motivation", (double) motivation);
        f.put("free_time_min", (double) freeTimeMin);
        f.put("missed_days", (double) missedDays);
        f.put("workout_frequency_7d", (double) workoutFrequency7d);
        f.put("risk_rank", (double) ConditionExpression.riskRank(riskLevel));
        return f;
    }
}
