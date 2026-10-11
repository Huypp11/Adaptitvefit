package com.adaptivefit.engine;

/**
 * Muc nguy co TAM TINH theo lich su tap (chua dung model ML). Phase 7 se thay bang diem tu model;
 * cau truc cac quy tac khong doi vi chung chi doc truong risk_rank.
 */
public final class HistoryRisk {

    private HistoryRisk() {}

    public static String level(int missedDays, int workoutFrequency7d) {
        return level(missedDays, workoutFrequency7d, 7);
    }

    /** observedDays: so ngay lich su thuc co (toi da 7). Nguoi moi chua co du lich su thi khong bi coi la it tap. */
    public static String level(int missedDays, int workoutFrequency7d, int observedDays) {
        if (missedDays >= 4) return "HIGH";
        boolean lowFrequency = observedDays >= 7 && workoutFrequency7d <= 1;
        if (missedDays >= 2 || lowFrequency) return "MEDIUM";
        return "LOW";
    }
}
