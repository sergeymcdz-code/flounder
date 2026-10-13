package com.acme.sportplatform.events;

import java.util.Set;
import java.util.UUID;

public interface EventLookup {

    Set<UUID> accessibleEventIds(UUID userId);

    EventLookupResult getById(UUID eventId);

    boolean isCreatedBy(UUID eventId, UUID userId);

    String getPublicSlug(UUID eventId);
}
