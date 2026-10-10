package com.adaptivefit.controller;

import com.adaptivefit.dto.PageResponse;
import com.adaptivefit.dto.WorkoutResponse;
import com.adaptivefit.dto.WorkoutStartRequest;
import com.adaptivefit.dto.WorkoutSummary;
import com.adaptivefit.service.WorkoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workouts")
@RequiredArgsConstructor
public class WorkoutController {

    private final WorkoutService workoutService;

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse start(@Valid @RequestBody WorkoutStartRequest req) {
        return workoutService.start(req);
    }

    @PostMapping("/{id}/complete")
    public WorkoutResponse complete(@PathVariable Long id) {
        return workoutService.complete(id);
    }

    @PostMapping("/{id}/abandon")
    public WorkoutResponse abandon(@PathVariable Long id) {
        return workoutService.abandon(id);
    }

    @GetMapping("/{id}")
    public WorkoutResponse get(@PathVariable Long id) {
        return workoutService.get(id);
    }

    @GetMapping
    public PageResponse<WorkoutSummary> history(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return workoutService.history(page, size);
    }
}
