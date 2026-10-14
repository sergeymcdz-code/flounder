package com.acme.sportplatform.registrations;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Published in the upload transaction; notification listeners should run after commit. */
public record ParticipantDocumentUploadedEvent(UUID documentId, UUID profileId, UUID eventId,
        UUID uploadedBy, String type, OffsetDateTime uploadedAt) {}
