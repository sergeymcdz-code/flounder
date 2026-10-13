package com.acme.sportplatform.registrations.api;

import com.acme.sportplatform.registrations.domain.RegistrationStatus;

import jakarta.validation.constraints.Positive;

public record PatchRegistrationRequest(
        @Positive(message = "Заявочное время должно быть положительным") Long seedTimeMs,
        RegistrationStatus status) {}
