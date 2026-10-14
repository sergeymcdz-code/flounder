package com.acme.sportplatform.registrations;

import java.util.UUID;

public record RegistrationRejectedEvent(UUID registrationId, String reason) {}
