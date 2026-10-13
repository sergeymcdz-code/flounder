package com.acme.sportplatform.registrations.api;

import java.time.*;
import java.util.UUID;
public record ParticipantDocumentResponse(UUID id, UUID profileId, UUID eventId, String type,
        String originalName, String contentType, long sizeBytes, LocalDate issuedAt, LocalDate validUntil,
        String status, String rejectReason, OffsetDateTime uploadedAt) {}
