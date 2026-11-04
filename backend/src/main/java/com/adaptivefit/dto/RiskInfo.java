package com.adaptivefit.dto;

import java.util.List;

/**
 * Ket qua danh gia nguy co gui cho giao dien. source = "model" (du doan cua mo hinh ML kem giai thich SHAP)
 * hoac "history_rule" (uoc tinh tam theo lich su khi dich vu AI chua san sang).
 */
public record RiskInfo(
        String source, Long predictionId, Double riskScore, String riskLevel, Double baseRisk,
        String modelVersion, String algorithm, boolean demoModel, String summary,
        List<Factor> factors, Double otherImpactPp, List<String> notes) {

    public record Factor(String feature, String label, double value, double reference,
                         String direction, double impactPp, String text) {}

    public static final String SOURCE_MODEL = "model";
    public static final String SOURCE_HISTORY = "history_rule";

    public static RiskInfo fromHistory(String level) {
        return new RiskInfo(SOURCE_HISTORY, null, null, level, null, null, null, false, null, List.of(), null,
                List.of("Dịch vụ AI chưa kết nối hoặc chưa có mô hình được kích hoạt, nên mức nguy cơ được tạm tính theo lịch sử tập luyện."));
    }

    public RiskInfo withPredictionId(Long id) {
        return new RiskInfo(source, id, riskScore, riskLevel, baseRisk, modelVersion, algorithm, demoModel,
                summary, factors, otherImpactPp, notes);
    }
}
