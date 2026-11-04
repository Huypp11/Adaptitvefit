package com.adaptivefit.service;

import com.adaptivefit.entity.ModelVersion;
import com.adaptivefit.entity.RiskPrediction;
import com.adaptivefit.repository.ModelVersionRepository;
import com.adaptivefit.repository.RiskPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Các thao tác đọc/ghi độc lập có thể dùng REQUIRES_NEW. Riêng prediction có thể tham gia transaction
 * recommendation đang giữ khóa check-in để tránh transaction con chờ khóa ngoại trên chính hàng cha đó.
 */
@Component
@RequiredArgsConstructor
public class RiskPredictionStore {

    private final RiskPredictionRepository predictions;
    private final ModelVersionRepository models;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<RiskPrediction> find(Long checkinId) {
        return predictions.findByCheckinId(checkinId);
    }

    /** Nem DataIntegrityViolationException neu da co du doan cho check-in nay (nguoi goi se doc lai). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RiskPrediction save(RiskPrediction p) {
        return predictions.saveAndFlush(p);
    }

    /** Joins the recommendation transaction which owns the pessimistically locked check-in row. */
    @Transactional
    public RiskPrediction saveInCurrentTransaction(RiskPrediction p) {
        return predictions.saveAndFlush(p);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ModelVersion ensureModelVersion(String version, String algorithm) {
        return models.findByVersion(version).orElseGet(() -> {
            ModelVersion m = new ModelVersion();
            m.setVersion(version);
            m.setAlgorithm(algorithm == null ? "unknown" : algorithm);
            m.setStatus("ACTIVE");
            return models.saveAndFlush(m);
        });
    }
}
