package com.acme.sportplatform.registrations;

import java.util.UUID;

public record RegistrationWaitlistPromotedEvent(UUID registrationId, UUID eventId, UUID eventDisciplineId,
        UUID participantProfileId, Integer waitlistPosition) {}
