package com.acme.sportplatform.registrations;

import java.util.UUID;

/** A fee assigned during import; does not announce a registration confirmation. */
public record RegistrationFeeAssignedEvent(UUID registrationId) {}
