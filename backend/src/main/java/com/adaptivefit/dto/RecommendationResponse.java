package com.adaptivefit.dto;

import com.adaptivefit.engine.RecommendationResult;

import java.util.List;

public record RecommendationResponse(Strategy strategy, State state, RecommendationResult recommendation) {

    public record Strategy(String code, String description, List<String> reasons) {}

    /** Tom tat trang thai dung de ra quyet dinh, de nguoi dung thay he thong dua tren gi. */
    public record State(double sleepHours, int stress, int energy, int freeTimeMin, int missedDays,
                        int workoutFrequency7d, String riskLevel, String riskSource) {}
}
