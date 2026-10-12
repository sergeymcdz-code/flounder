package com.acme.sportplatform.registrations;

import java.util.UUID;

public record RegistrationLookupResult(
        UUID id,
        UUID participantUserId,
        UUID eventDisciplineId,
        String status,
        String participantSnapshot,
        UUID eventId,
        UUID participantProfileId,
        Long seedTimeMs,
        String source,
        String paymentStatus) {}
