package com.adaptivefit.service;

import com.adaptivefit.client.AiServiceClient;
import com.adaptivefit.entity.DailyCheckin;
import com.adaptivefit.entity.ModelVersion;
import com.adaptivefit.entity.RiskPrediction;
import com.adaptivefit.repository.DailyCheckinRepository;
import com.adaptivefit.repository.RiskPredictionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    @Mock private AiServiceClient ai;
    @Mock private RiskPredictionStore store;
    @Mock private DailyCheckinRepository checkinRepository;
    @Mock private RiskPredictionRepository predictionRepository;
    private RiskService riskService;

    private DailyCheckin checkin;
    private Map<String, Double> features;

    @BeforeEach
    void setUp() {
        riskService = new RiskService(ai, store, new ObjectMapper(), checkinRepository, predictionRepository);
        checkin = new DailyCheckin();
        checkin.setId(17L);
        features = Map.of("sleep_hours", 7.0);

        ModelVersion modelVersion = new ModelVersion();
        modelVersion.setId(3L);
        when(store.find(17L)).thenReturn(Optional.empty());
        when(ai.predict(features)).thenReturn(Optional.of(new AiServiceClient.AiPrediction(
                0.4, "MEDIUM", 0.3, "v2.0", "logreg", true,
                java.util.List.of(), 0.0, "summary", java.util.List.of())));
        when(store.ensureModelVersion("v2.0", "logreg")).thenReturn(modelVersion);
        lenient().when(store.save(any(RiskPrediction.class))).thenAnswer(invocation -> {
            RiskPrediction prediction = invocation.getArgument(0);
            prediction.setId(51L);
            return prediction;
        });
        lenient().when(store.saveInCurrentTransaction(any(RiskPrediction.class))).thenAnswer(invocation -> {
            RiskPrediction prediction = invocation.getArgument(0);
            prediction.setId(52L);
            return prediction;
        });
    }

    @Test
    void recommendationAssessmentSavesPredictionInCallerTransaction() {
        var result = riskService.assessForRecommendation(checkin, features, "LOW");

        assertEquals(52L, result.predictionId());
        verify(store).saveInCurrentTransaction(any(RiskPrediction.class));
        verify(store, never()).save(any(RiskPrediction.class));
    }

    @Test
    void regularAssessmentKeepsIndependentPredictionTransaction() {
        var result = riskService.assess(checkin, features, "LOW");

        assertEquals(51L, result.predictionId());
        verify(store).save(any(RiskPrediction.class));
        verify(store, never()).saveInCurrentTransaction(any(RiskPrediction.class));
    }
}
