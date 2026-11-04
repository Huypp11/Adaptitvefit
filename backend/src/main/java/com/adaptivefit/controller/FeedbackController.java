package com.adaptivefit.controller;

import com.adaptivefit.dto.FeedbackRequest;
import com.adaptivefit.dto.FeedbackResponse;
import com.adaptivefit.dto.RecommendationFeedbackRequest;
import com.adaptivefit.dto.RecommendationFeedbackResponse;
import com.adaptivefit.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse create(@Valid @RequestBody FeedbackRequest req) {
        return feedbackService.create(req);
    }

    @GetMapping
    public List<FeedbackResponse> mine() {
        return feedbackService.myFeedback();
    }

    @PostMapping("/recommendations/{recommendationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public RecommendationFeedbackResponse createForRecommendation(
            @PathVariable Long recommendationId, @Valid @RequestBody RecommendationFeedbackRequest req) {
        return feedbackService.createForRecommendation(recommendationId, req);
    }

    @PutMapping("/recommendations/{recommendationId}")
    public RecommendationFeedbackResponse updateForRecommendation(
            @PathVariable Long recommendationId, @Valid @RequestBody RecommendationFeedbackRequest req) {
        return feedbackService.updateForRecommendation(recommendationId, req);
    }
}
