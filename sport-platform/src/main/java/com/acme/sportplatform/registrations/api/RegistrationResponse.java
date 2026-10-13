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
        OffsetDateTime updatedAt,
        Long seedTimeMs,
        String seedTime,
        String source,
        String paymentStatus,
        Integer waitlistPosition) {
    public RegistrationResponse(
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
        OffsetDateTime updatedAt,
        Long seedTimeMs,
        String seedTime,
        String source,
        String paymentStatus) {
        this(id, eventId, eventDisciplineId, participantUserId, participantProfileId, status, participantSnapshot, registrationMeta, reviewNote, reviewedByUserId, reviewedAt, submittedAt, createdAt, updatedAt, seedTimeMs, seedTime, source, paymentStatus, null);
    }
}
