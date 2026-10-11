package com.adaptivefit.engine;

import java.util.List;
import java.util.Map;

public record RecommendationResult(int budgetMinutes, List<ScoredExercise> items, List<PlanItem> plan,
                                   Map<String, Integer> excluded, String message) {

    /** Mot bai trong buoi tap goi y, thoi gian da chia sao cho tong khong vuot qua quy thoi gian. */
    public record PlanItem(long exerciseId, String name, int plannedMin) {}
}
