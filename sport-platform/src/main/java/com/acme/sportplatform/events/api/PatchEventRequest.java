package com.acme.sportplatform.events.api;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.Positive;

public record PatchEventRequest(
        @Positive(message = "Лимит должен быть положительным") Integer maxParticipants,
        OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt, com.acme.sportplatform.events.DiplomaSettings diploma) {}
