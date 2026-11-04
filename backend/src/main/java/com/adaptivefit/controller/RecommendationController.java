package com.adaptivefit.controller;

import com.adaptivefit.dto.RecommendationResponse;
import com.adaptivefit.dto.PageResponse;
import com.adaptivefit.observability.PerformanceTiming;
import com.adaptivefit.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/today")
    public RecommendationResponse today() {
        return PerformanceTiming.measure("controller.recommendation.today", () -> {
            return recommendationService.today();
        });
    }

    @GetMapping("/history")
    public PageResponse<RecommendationResponse> history(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return recommendationService.history(page, size);
    }
}
