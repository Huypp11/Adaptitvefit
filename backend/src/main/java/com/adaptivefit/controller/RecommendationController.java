package com.adaptivefit.controller;

import com.adaptivefit.dto.RecommendationResponse;
import com.adaptivefit.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/today")
    public RecommendationResponse today() {
        return recommendationService.today();
    }
}
