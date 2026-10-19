package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationEventLookup {
    Optional<Context> findNotificationContext(UUID eventId);
    record Context(UUID id, UUID organizationId, String title, OffsetDateTime startsAt, String publicSlug, UUID venueId) {}
}
