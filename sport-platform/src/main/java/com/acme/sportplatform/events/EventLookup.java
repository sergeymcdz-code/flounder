package com.acme.sportplatform.events;

import java.util.UUID;

public interface EventLookup {

    EventLookupResult getById(UUID eventId);
}