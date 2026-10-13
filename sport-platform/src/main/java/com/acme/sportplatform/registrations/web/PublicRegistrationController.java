package com.acme.sportplatform.registrations.web;

import com.acme.sportplatform.registrations.api.*;
import com.acme.sportplatform.registrations.application.PublicRegistrationUseCase;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/public/events/{slug}")
public class PublicRegistrationController {
    private final PublicRegistrationUseCase registrations;

    public PublicRegistrationController(PublicRegistrationUseCase registrations) {
        this.registrations = registrations;
    }

    @GetMapping("/registration-form")
    public RegistrationFormResponse form(@PathVariable String slug) {
        return registrations.form(slug);
    }

    @PostMapping("/registrations")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public List<RegistrationResponse> submit(
            @PathVariable String slug,
            @AuthenticationPrincipal(expression = "userId") UUID userId,
            @RequestBody @Valid PublicRegistrationRequest r) {
        return registrations.submit(slug, userId, r);
    }
}
