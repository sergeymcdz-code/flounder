package com.acme.sportplatform.results.api;

import java.util.List;
import java.util.UUID;

public record PublicDisciplineResultsResponse(
        UUID eventId,
        UUID eventDisciplineId,
        List<UnitView> units,
        boolean official
) {
    public record UnitView(
            UUID id,
            String label,
            Integer sequenceNumber,
            List<EntryView> entries,
            boolean official
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