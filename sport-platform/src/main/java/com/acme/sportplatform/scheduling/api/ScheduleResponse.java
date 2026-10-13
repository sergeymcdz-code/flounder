package com.acme.sportplatform.scheduling.api;

import com.acme.sportplatform.scheduling.domain.Violation;

import java.time.Instant;
import java.util.*;

public record ScheduleResponse(List<Unit> units, List<Violation> violations) {
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Unit(UUID id, int number, Instant scheduledAt, Integer sequenceInSession) {}
}
