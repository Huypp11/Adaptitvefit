package com.adaptivefit.controller;

import com.adaptivefit.dto.FeedbackRequest;
import com.adaptivefit.dto.FeedbackResponse;
import com.adaptivefit.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
}
