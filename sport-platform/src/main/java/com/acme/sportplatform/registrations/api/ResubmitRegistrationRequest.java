package com.acme.sportplatform.registrations.api;

import com.acme.sportplatform.identity.ProfileData;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

public record ResubmitRegistrationRequest(
        @Valid ProfileData profile,
        @Positive(message = "Заявочное время должно быть положительным") Long seedTimeMs,
        String seedTime) {}
