package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SessionHeatsLookup {
    Summary summary(UUID sessionId);

    record Summary(long heatsCount, OffsetDateTime estimatedEndsAt) {}
}
