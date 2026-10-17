package com.acme.sportplatform.events;

import java.util.Set;
import java.util.UUID;

public interface EventLookup {

    java.util.Map<UUID, java.time.LocalDate> startDates(java.util.Collection<UUID> eventIds);

    java.util.Map<UUID, String> settingsJson(java.util.Collection<UUID> eventIds);

    Set<UUID> accessibleEventIds(UUID userId);

    EventLookupResult getById(UUID eventId);

    EventProtocolHeader protocolHeader(UUID eventId);

    EventLookupResult lockById(UUID eventId);
}
