package com.acme.sportplatform.scheduling.api;

import java.util.*;

public record EventGenerationResponse(List<Discipline> disciplines, long durationMs) {
    public record Discipline(UUID eventDisciplineId, GenerationResponse result) {}
}
