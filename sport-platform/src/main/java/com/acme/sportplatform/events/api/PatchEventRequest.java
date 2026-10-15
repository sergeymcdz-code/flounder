package com.acme.sportplatform.events.api;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.Positive;

public record PatchEventRequest(
        @Positive(message = "Лимит должен быть положительным") Integer maxParticipants,
        OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
        @jakarta.validation.Valid com.acme.sportplatform.events.EventPaymentSettings payment) {
    public PatchEventRequest(Integer maxParticipants, OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt) {
        this(maxParticipants, registrationOpenAt, registrationCloseAt, null);
    }
}
