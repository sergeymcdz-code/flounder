package com.acme.sportplatform.events.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
        @NotNull UUID organizationId,
        @NotNull UUID venueId,
        @NotNull UUID sportId,
        @NotNull UUID regulationVersionId,
        @NotBlank String title,
        String description,
        OffsetDateTime registrationOpenAt,
        OffsetDateTime registrationCloseAt,
        @NotNull OffsetDateTime eventStartAt,
        @NotNull OffsetDateTime eventEndAt
) {
}