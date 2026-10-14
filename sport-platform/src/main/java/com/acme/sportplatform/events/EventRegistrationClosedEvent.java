package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventRegistrationClosedEvent(UUID eventId, OffsetDateTime occurredAt) {}
