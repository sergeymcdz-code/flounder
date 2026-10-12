package com.acme.sportplatform.registrations;

import java.util.UUID;

public record RegistrationWithdrawn(UUID registrationId, UUID eventId, UUID eventDisciplineId) {}
