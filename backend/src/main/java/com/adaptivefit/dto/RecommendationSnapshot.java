package com.adaptivefit.dto;

import com.adaptivefit.engine.RecommendationResult;

public record RecommendationSnapshot(
        RecommendationResponse.Strategy strategy,
        RecommendationResponse.State state,
        RiskInfo risk,
        RecommendationResult recommendation
) {}
