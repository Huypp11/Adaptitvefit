package com.adaptivefit.engine;

import java.util.List;

public record ScoredExercise(long exerciseId, String name, int plannedMin, double score,
                             Breakdown breakdown, List<String> reasons) {

    /** Diem tung thanh phan (0-1, chua nhan trong so) de giai thich duoc ket qua. */
    public record Breakdown(double time, double energy, double goal, double difficulty,
                            double preference, double recovery) {}
}
