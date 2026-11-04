package com.adaptivefit.controller;

import com.adaptivefit.dto.FeedbackStatsResponse;
import com.adaptivefit.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping("/stats")
    public FeedbackStatsResponse statistics() {
        return feedbackService.statistics();
    }
}
