package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventRegistrationOpenedEvent(UUID eventId, OffsetDateTime occurredAt) {}
