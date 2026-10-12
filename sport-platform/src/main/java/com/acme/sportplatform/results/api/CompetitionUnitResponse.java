package com.acme.sportplatform.results.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompetitionUnitResponse(
        UUID id,
        UUID eventDisciplineId,
        String label,
        Integer sequenceNumber,
        String status,
        OffsetDateTime scheduledAt
) {}