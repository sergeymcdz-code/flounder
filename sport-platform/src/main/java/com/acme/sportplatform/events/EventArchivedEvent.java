package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventArchivedEvent(UUID eventId, OffsetDateTime occurredAt) {}
