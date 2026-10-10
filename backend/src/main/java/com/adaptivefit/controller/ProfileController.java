package com.adaptivefit.controller;

import com.adaptivefit.dto.ProfileRequest;
import com.adaptivefit.dto.ProfileResponse;
import com.adaptivefit.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileResponse get() {
        return profileService.get();
    }

    @PutMapping
    public ProfileResponse upsert(@Valid @RequestBody ProfileRequest req) {
        return profileService.upsert(req);
    }
}
