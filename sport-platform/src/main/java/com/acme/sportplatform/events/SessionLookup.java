package com.acme.sportplatform.events;

import java.util.UUID;

public interface SessionLookup {
    void assertBelongsToEvent(UUID sessionId, UUID eventId);
}
