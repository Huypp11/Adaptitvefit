package com.adaptivefit.controller;

import com.adaptivefit.dto.RecommendationEventRequest;
import com.adaptivefit.service.RecommendationEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendation-events")
@RequiredArgsConstructor
public class RecommendationEventController {

    private final RecommendationEventService eventService;

    @PostMapping
    public RecordResult record(@Valid @RequestBody RecommendationEventRequest request) {
        return new RecordResult(eventService.recordClientEvent(request));
    }

    public record RecordResult(boolean recorded) {}
}
