package com.adaptivefit.service;

import com.adaptivefit.client.AiServiceClient;
import com.adaptivefit.dto.RiskInfo;
import com.adaptivefit.dto.RiskPoint;
import com.adaptivefit.entity.DailyCheckin;
import com.adaptivefit.entity.RiskPrediction;
import com.adaptivefit.entity.User;
import com.adaptivefit.observability.PerformanceTiming;
import com.adaptivefit.repository.DailyCheckinRepository;
import com.adaptivefit.repository.RiskPredictionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Danh gia nguy co cho mot check-in: dung du doan da luu neu co; neu khong thi goi dich vu AI, luu ket qua
 * (kem giai thich SHAP) roi tra ve. Neu AI khong san sang thi tra ve uoc tinh theo lich su (khong luu).
 */
@Service
public class RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskService.class);

    private final AiServiceClient ai;
    private final RiskPredictionStore store;
    private final ObjectMapper mapper;
    private final DailyCheckinRepository checkinRepository;
    private final RiskPredictionRepository predictionRepository;

    public RiskService(AiServiceClient ai, RiskPredictionStore store, ObjectMapper mapper,
                       DailyCheckinRepository checkinRepository, RiskPredictionRepository predictionRepository) {
        this.ai = ai;
        this.store = store;
        this.mapper = mapper;
        this.checkinRepository = checkinRepository;
        this.predictionRepository = predictionRepository;
    }

    public RiskInfo assess(DailyCheckin checkin, Map<String, Double> features, String fallbackLevel) {
        return PerformanceTiming.measure("risk.assess",
                () -> assessInternal(checkin, features, fallbackLevel, false));
    }

    public RiskInfo assessForRecommendation(DailyCheckin checkin, Map<String, Double> features,
                                            String fallbackLevel) {
        return PerformanceTiming.measure("risk.assess",
                () -> assessInternal(checkin, features, fallbackLevel, true));
    }

    private RiskInfo assessInternal(DailyCheckin checkin, Map<String, Double> features, String fallbackLevel,
                                    boolean joinCurrentTransaction) {
        Optional<RiskPrediction> existing = PerformanceTiming.measure("risk.prediction_lookup",
                () -> store.find(checkin.getId()));
        if (existing.isPresent()) {
            RiskInfo parsed = parse(existing.get());
            if (parsed != null) {
                PerformanceTiming.event("risk.prediction_cache", "result=hit");
                return parsed;
            }
        }
        PerformanceTiming.event("risk.prediction_cache", "result=miss");

        Optional<AiServiceClient.AiPrediction> result = PerformanceTiming.measure("risk.ai_client_call",
                () -> ai.predict(features));
        if (result.isEmpty()) {
            PerformanceTiming.event("risk.result", "source=history_fallback");
            return RiskInfo.fromHistory(fallbackLevel);
        }
        PerformanceTiming.event("risk.result", "source=ai_model");

        RiskInfo info = toInfo(result.get());
        RiskPrediction row = new RiskPrediction();
        row.setCheckinId(checkin.getId());
        row.setRiskScore(info.riskScore());
        row.setRiskLevel(info.riskLevel());
        row.setShapJson(toJson(info));
        row.setModelVersionId(PerformanceTiming.measure("risk.model_version_persist",
                () -> modelVersionId(info.modelVersion(), info.algorithm())));

        RiskPrediction saved;
        if (joinCurrentTransaction) {
            saved = PerformanceTiming.measure("risk.prediction_persist",
                    () -> store.saveInCurrentTransaction(row));
        } else {
            try {
                saved = PerformanceTiming.measure("risk.prediction_persist", () -> store.save(row));
            } catch (DataIntegrityViolationException raced) {
                PerformanceTiming.event("risk.prediction_persist", "result=unique_conflict_reread");
                saved = PerformanceTiming.measure("risk.prediction_conflict_reread",
                        () -> store.find(checkin.getId()).orElse(null));   // yeu cau khac vua luu truoc
            }
        }
        return saved == null ? info : info.withPredictionId(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<RiskPoint> history(User user, int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        List<DailyCheckin> checkins = checkinRepository
                .findByUserIdAndCheckinDateBetweenOrderByCheckinDateDesc(user.getId(), from, to);
        if (checkins.isEmpty()) return List.of();
        Map<Long, DailyCheckin> byId = checkins.stream().collect(Collectors.toMap(DailyCheckin::getId, c -> c));
        List<RiskPoint> points = new ArrayList<>();
        for (RiskPrediction p : predictionRepository.findByCheckinIdIn(byId.keySet())) {
            points.add(new RiskPoint(byId.get(p.getCheckinId()).getCheckinDate(), p.getRiskScore(), p.getRiskLevel()));
        }
        points.sort(Comparator.comparing(RiskPoint::date));
        return points;
    }

    private Long modelVersionId(String version, String algorithm) {
        if (version == null) return null;
        try {
            return store.ensureModelVersion(version, algorithm).getId();
        } catch (DataIntegrityViolationException raced) {
            return store.ensureModelVersion(version, algorithm).getId();
        }
    }

    private RiskInfo toInfo(AiServiceClient.AiPrediction p) {
        List<RiskInfo.Factor> factors = p.factors() == null ? List.of() : p.factors().stream()
                .map(f -> new RiskInfo.Factor(f.feature(), f.label(), f.value(), f.reference(), f.direction(),
                        f.impactPp(), f.text()))
                .toList();
        return new RiskInfo(RiskInfo.SOURCE_MODEL, null, p.riskScore(), p.riskLevel(), p.baseRisk(), p.modelVersion(),
                p.algorithm(), p.demoModel(), p.summary(), factors, p.otherImpactPp(),
                p.notes() == null ? List.of() : p.notes());
    }

    private String toJson(RiskInfo info) {
        try {
            return mapper.writeValueAsString(info);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Khong luu duoc giai thich du doan", e);
        }
    }

    private RiskInfo parse(RiskPrediction row) {
        try {
            return mapper.readValue(row.getShapJson(), RiskInfo.class).withPredictionId(row.getId());
        } catch (Exception e) {
            log.warn("Du doan #{} co JSON khong doc duoc, se tinh lai: {}", row.getId(), e.getMessage());
            return null;
        }
    }
}
