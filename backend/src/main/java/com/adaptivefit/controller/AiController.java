package com.adaptivefit.controller;

import com.adaptivefit.dto.RiskInfo;
import com.adaptivefit.dto.RiskPoint;
import com.adaptivefit.observability.PerformanceTiming;
import com.adaptivefit.service.CurrentUserService;
import com.adaptivefit.service.RecommendationService;
import com.adaptivefit.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final RecommendationService recommendationService;
    private final RiskService riskService;
    private final CurrentUserService currentUser;

    /** Nguy co hom nay kem giai thich SHAP (404 neu chua check-in). */
    @GetMapping("/today")
    public RiskInfo today() {
        return PerformanceTiming.measure("controller.ai.today", recommendationService::todayRisk);
    }

    @GetMapping("/risk-history")
    public List<RiskPoint> history(@RequestParam(defaultValue = "30") int days) {
        return riskService.history(currentUser.get(), Math.min(Math.max(days, 1), 90));
    }
}
