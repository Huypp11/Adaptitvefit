package com.adaptivefit.dto;

import java.time.LocalDate;

public record RiskPoint(LocalDate date, double riskScore, String riskLevel) {}
