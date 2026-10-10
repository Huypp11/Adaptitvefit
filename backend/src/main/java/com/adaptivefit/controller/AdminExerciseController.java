package com.adaptivefit.controller;

import com.adaptivefit.dto.ExerciseRequest;
import com.adaptivefit.dto.ExerciseResponse;
import com.adaptivefit.dto.PageResponse;
import com.adaptivefit.service.ExerciseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** CRUD bai tap cho ADMIN (quyen duoc chan o SecurityConfig: /api/admin/** -> ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin/exercises")
@RequiredArgsConstructor
public class AdminExerciseController {

    private final ExerciseService exerciseService;

    @GetMapping
    public PageResponse<ExerciseResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String intensity,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return exerciseService.search(q, tag, location, intensity, active, page, size);
    }

    @GetMapping("/{id}")
    public ExerciseResponse get(@PathVariable Long id) {
        return exerciseService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseResponse create(@Valid @RequestBody ExerciseRequest req) {
        return exerciseService.create(req);
    }

    @PutMapping("/{id}")
    public ExerciseResponse update(@PathVariable Long id, @Valid @RequestBody ExerciseRequest req) {
        return exerciseService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        exerciseService.deactivate(id);
    }
}
