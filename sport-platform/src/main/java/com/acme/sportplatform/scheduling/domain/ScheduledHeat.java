package com.acme.sportplatform.scheduling.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScheduledHeat(
        UUID disciplineId,
        int number,
        Instant scheduledAt,
        int sequenceInSession,
        List<UUID> athleteIds) {
    public ScheduledHeat {
        athleteIds = List.copyOf(athleteIds);
    }
}
