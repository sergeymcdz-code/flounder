package com.acme.sportplatform.scheduling;

import java.util.UUID;

// A null disciplineId denotes publication of the entire event.
public record StartListPublishedEvent(UUID publicationId, UUID eventId, UUID disciplineId) {}
