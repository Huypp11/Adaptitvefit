package com.adaptivefit.controller;

import com.adaptivefit.dto.ExerciseResponse;
import com.adaptivefit.dto.PageResponse;
import com.adaptivefit.service.ExerciseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Thu vien bai tap cho USER (chi bai tap dang bat). */
@RestController
@RequestMapping("/api/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    @GetMapping
    public PageResponse<ExerciseResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String intensity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return exerciseService.search(q, tag, location, intensity, true, page, size);
    }

    @GetMapping("/{id}")
    public ExerciseResponse get(@PathVariable Long id) {
        return exerciseService.getActive(id);
    }
}
