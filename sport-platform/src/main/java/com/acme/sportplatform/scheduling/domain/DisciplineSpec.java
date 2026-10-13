package com.acme.sportplatform.scheduling.domain;

import java.util.List;
import java.util.UUID;

public record DisciplineSpec(
        UUID id,
        int number,
        int distanceM,
        String stroke,
        String gender,
        List<AgeGroupSpec> ageGroups,
        String seedingMode,
        UUID sessionId,
        int orderInSession,
        Integer heatDurationSec) {
    public DisciplineSpec {
        ageGroups = List.copyOf(ageGroups);
    }

    public DisciplineSpec(
            UUID id,
            int number,
            int distanceM,
            String stroke,
            String gender,
            List<AgeGroupSpec> ageGroups,
            String seedingMode) {
        this(id, number, distanceM, stroke, gender, ageGroups, seedingMode, null, number, null);
    }
}
