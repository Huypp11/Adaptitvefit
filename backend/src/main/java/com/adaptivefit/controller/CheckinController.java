package com.adaptivefit.controller;

import com.adaptivefit.dto.CheckinRequest;
import com.adaptivefit.dto.CheckinResponse;
import com.adaptivefit.service.CheckinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/checkins")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckinResponse create(@Valid @RequestBody CheckinRequest req) {
        return checkinService.create(req);
    }

    @GetMapping("/today")
    public CheckinResponse today() {
        return checkinService.today();
    }

    @GetMapping
    public List<CheckinResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return checkinService.list(from, to);
    }
}
