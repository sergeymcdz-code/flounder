package com.acme.sportplatform.results.api;

import java.util.List;
import java.util.UUID;

public record PublicDisciplineResultsResponse(
        UUID eventId,
        UUID eventDisciplineId,
        List<UnitView> units
) {
    public record UnitView(
            UUID id,
            String label,
            Integer sequenceNumber,
            List<EntryView> entries
    ) {
    }

    public record EntryView(
            Integer place,
            String participantName,
            String clubName,
            String rawValue,
            String resultType,
            String status
    ) {
    }
}