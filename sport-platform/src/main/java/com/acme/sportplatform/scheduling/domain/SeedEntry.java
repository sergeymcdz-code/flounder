package com.acme.sportplatform.scheduling.domain;

import java.util.UUID;

public record SeedEntry(
        UUID registrationId,
        UUID athleteId,
        String lastName,
        String firstName,
        Integer birthYear,
        String gender,
        String club,
        Long seedTimeMs) {}
