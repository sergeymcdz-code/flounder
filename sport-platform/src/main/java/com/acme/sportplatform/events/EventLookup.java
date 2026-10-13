package com.acme.sportplatform.events;

import java.util.Set;
import java.util.UUID;

public interface EventLookup {

    Set<UUID> accessibleEventIds(UUID userId);

    EventLookupResult getById(UUID eventId);

    EventProtocolHeader protocolHeader(UUID eventId);

    EventLookupResult lockById(UUID eventId);
}
