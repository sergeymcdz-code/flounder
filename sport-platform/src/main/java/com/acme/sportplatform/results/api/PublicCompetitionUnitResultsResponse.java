package com.acme.sportplatform.results.api;

import java.util.List;
import java.util.UUID;

public record PublicCompetitionUnitResultsResponse(
        UUID id,
        UUID eventDisciplineId,
        String label,
        List<EntryView> entries
) {
    public record EntryView(
            String participantName,
            Integer laneOrPosition,
            String rawValue,
            String resultType,
            String resultStatus,
            Integer finalPlace
    ) {
    }
}