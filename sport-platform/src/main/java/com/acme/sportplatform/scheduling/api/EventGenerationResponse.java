package com.acme.sportplatform.scheduling.api;

import com.acme.sportplatform.scheduling.domain.Violation;

import java.util.*;

public record EventGenerationResponse(
        List<Discipline> disciplines, List<Violation> violations, long durationMs) {
    public record Discipline(UUID eventDisciplineId, GenerationResponse result) {}
}
