package com.acme.sportplatform.results.api;

import java.util.List;
import java.util.UUID;

public record CompetitionUnitDetailsResponse(
        UUID id,
        UUID eventDisciplineId,
        String label,
        String status,
        List<EntryView> entries
) {
    public record EntryView(
            UUID entryId,
            String participantName,
            UUID registrationId,
            Integer laneOrPosition,
            String rawValue,
            String resultType,
            String resultStatus,
            Integer finalPlace
    ) {
    }
}