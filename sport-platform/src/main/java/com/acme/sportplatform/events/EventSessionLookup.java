package com.acme.sportplatform.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventSessionLookup {
    record Session(UUID id, int orderNo, Instant startsAt, int breakBetweenEventsSec) {}

    List<Session> findByEventId(UUID eventId);
}
