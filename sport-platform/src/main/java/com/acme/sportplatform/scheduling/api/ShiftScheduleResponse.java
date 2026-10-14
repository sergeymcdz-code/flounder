package com.acme.sportplatform.scheduling.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record ShiftScheduleResponse(List<Unit> units, OffsetDateTime shiftedAt) {
    public record Unit(UUID id, Instant previousScheduledAt, Instant scheduledAt) {}
}
