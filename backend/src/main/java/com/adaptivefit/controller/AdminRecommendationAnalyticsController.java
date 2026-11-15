package com.adaptivefit.controller;

import com.adaptivefit.dto.RecommendationAnalyticsResponse;
import com.adaptivefit.service.RecommendationEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/recommendation-analytics")
@RequiredArgsConstructor
public class AdminRecommendationAnalyticsController {

    private final RecommendationEventService eventService;

    @GetMapping
    public RecommendationAnalyticsResponse analytics() {
        return eventService.analytics();
    }
}
