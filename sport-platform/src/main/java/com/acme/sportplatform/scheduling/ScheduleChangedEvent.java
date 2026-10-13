package com.acme.sportplatform.scheduling;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Emitted within the shift transaction; consumers can listen after commit. */
public record ScheduleChangedEvent(UUID eventId, List<UUID> unitIds, OffsetDateTime changedAt) {
    public ScheduleChangedEvent { unitIds = List.copyOf(unitIds); }
}
