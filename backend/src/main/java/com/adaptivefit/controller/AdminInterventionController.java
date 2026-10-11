package com.adaptivefit.controller;

import com.adaptivefit.dto.InterventionResponse;
import com.adaptivefit.dto.InterventionUpdateRequest;
import com.adaptivefit.service.InterventionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Quan ly quy tac can thiep (chi ADMIN: /api/admin/** da bi chan o SecurityConfig). */
@RestController
@RequestMapping("/api/admin/interventions")
@RequiredArgsConstructor
public class AdminInterventionController {

    private final InterventionService interventionService;

    @GetMapping
    public List<InterventionResponse> list() {
        return interventionService.list();
    }

    @PutMapping("/{id}")
    public InterventionResponse update(@PathVariable Long id, @Valid @RequestBody InterventionUpdateRequest req) {
        return interventionService.update(id, req);
    }
}
