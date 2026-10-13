package com.acme.sportplatform.scheduling.domain;

import java.util.List;
import java.util.UUID;

public record SeedingResult(
        List<SeededHeat> heats, List<Violation> violations, List<UUID> excludedRegistrationIds) {
    public SeedingResult {
        heats = List.copyOf(heats);
        violations = List.copyOf(violations);
        excludedRegistrationIds = List.copyOf(excludedRegistrationIds);
    }
}
