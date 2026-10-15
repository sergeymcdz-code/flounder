package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationEventLookup {
    java.util.List<UUID> paymentReminderEventIds(java.time.Instant now);
    Optional<Context> findNotificationContext(UUID eventId);
    record Context(UUID id, UUID organizationId, String title, OffsetDateTime startsAt, String publicSlug, String timezone) {
        public Context(UUID id,UUID organizationId,String title,OffsetDateTime startsAt,String publicSlug) {
            this(id,organizationId,title,startsAt,publicSlug,"UTC");
        }
    }
}
