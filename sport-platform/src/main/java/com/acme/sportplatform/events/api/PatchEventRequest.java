package com.acme.sportplatform.events.api;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.Positive;

public record PatchEventRequest(
        @Positive(message = "Лимит должен быть положительным") Integer maxParticipants,
        OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
        @jakarta.validation.Valid com.acme.sportplatform.events.EventPaymentSettings payment, com.acme.sportplatform.events.DiplomaSettings diploma) {
    public PatchEventRequest(Integer maxParticipants, OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt) {
        this(maxParticipants, registrationOpenAt, registrationCloseAt, null, null);
    }
    public PatchEventRequest(Integer maxParticipants, OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
            com.acme.sportplatform.events.EventPaymentSettings payment) {
        this(maxParticipants, registrationOpenAt, registrationCloseAt, payment, null);
    }
    public PatchEventRequest(Integer maxParticipants, OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
            com.acme.sportplatform.events.DiplomaSettings diploma) {
        this(maxParticipants, registrationOpenAt, registrationCloseAt, null, diploma);
    }
}
