package com.acme.sportplatform.results.api;

import java.util.UUID;

public record CreateCompetitionUnitRequest(
        UUID eventDisciplineId,
        String label,
        Integer sequenceNumber
) {}