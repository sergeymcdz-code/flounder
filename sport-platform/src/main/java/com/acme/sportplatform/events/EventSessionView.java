package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventSessionView(
        UUID id,
        UUID eventId,
        String name,
        int orderNo,
        OffsetDateTime startsAt,
        int breakBetweenEventsSec) {}
