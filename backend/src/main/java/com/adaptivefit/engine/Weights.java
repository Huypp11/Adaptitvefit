package com.adaptivefit.engine;

/** Trong so cua tung thanh phan diem; duoc chuan hoa de tong bang 1. */
public record Weights(double time, double energy, double goal, double difficulty, double preference, double recovery) {

    public static Weights defaults() {
        return new Weights(0.25, 0.20, 0.20, 0.15, 0.10, 0.10);
    }

    public Weights normalized() {
        double sum = time + energy + goal + difficulty + preference + recovery;
        if (sum <= 0) throw new IllegalArgumentException("Tổng trọng số phải lớn hơn 0");
        return new Weights(time / sum, energy / sum, goal / sum, difficulty / sum, preference / sum, recovery / sum);
    }
}
