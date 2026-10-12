package com.acme.sportplatform.registrations.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record RegistrationResponse(
        UUID id,
        UUID eventId,
        UUID eventDisciplineId,
        UUID participantUserId,
        UUID participantProfileId,
        String status,
        JsonNode participantSnapshot,
        JsonNode registrationMeta,
        String reviewNote,
        UUID reviewedByUserId,
        OffsetDateTime reviewedAt,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}