package com.adaptivefit.controller;

import com.adaptivefit.dto.RecommendationDataConsentRequest;
import com.adaptivefit.dto.RecommendationDataConsentResponse;
import com.adaptivefit.service.RecommendationEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendation-data-consent")
@RequiredArgsConstructor
public class RecommendationDataConsentController {

    private final RecommendationEventService eventService;

    @GetMapping
    public RecommendationDataConsentResponse get() {
        return eventService.consent();
    }

    @PutMapping
    public RecommendationDataConsentResponse update(@Valid @RequestBody RecommendationDataConsentRequest request) {
        return eventService.updateConsent(request);
    }
}
