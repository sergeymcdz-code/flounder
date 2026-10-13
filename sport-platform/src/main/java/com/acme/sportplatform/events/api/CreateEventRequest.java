package com.acme.sportplatform.events.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateEventRequest(
        @NotNull(message = "Укажите организацию") UUID organizationId,
        @NotNull(message = "Укажите площадку") UUID venueId,
        @NotNull(message = "Укажите вид спорта") UUID sportId,
        @NotNull(message = "Укажите версию регламента") UUID regulationVersionId,
        @NotBlank(message = "Укажите название события") String title,
        String description,
        OffsetDateTime registrationOpenAt,
        OffsetDateTime registrationCloseAt,
        @NotNull(message = "Укажите начало события") OffsetDateTime eventStartAt,
        @NotNull(message = "Укажите окончание события") OffsetDateTime eventEndAt
) {
}