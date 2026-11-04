package com.adaptivefit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "risk_predictions")
@Getter @Setter @NoArgsConstructor
public class RiskPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checkin_id", nullable = false, unique = true)
    private Long checkinId;

    @Column(name = "model_version_id")
    private Long modelVersionId;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    /** JSON cua RiskInfo (diem, muc, cac yeu to SHAP, loi giai thich). */
    @Column(name = "shap_json", length = 6000)
    private String shapJson;

    @Column(name = "intervention_id")
    private Long interventionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
