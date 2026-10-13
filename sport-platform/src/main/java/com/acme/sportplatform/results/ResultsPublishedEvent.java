package com.acme.sportplatform.results;

import java.util.List;
import java.util.UUID;

public record ResultsPublishedEvent(UUID competitionUnitId, UUID disciplineId, List<UUID> registrationIds) {
    public ResultsPublishedEvent {
        registrationIds = List.copyOf(registrationIds);
    }
}
