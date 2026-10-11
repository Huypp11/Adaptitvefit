package com.adaptivefit.engine;

import java.util.List;

public record StrategyDecision(String code, String description, List<String> reasons, StrategyParams params) {}
