package com.acme.sportplatform.results;

import java.util.List;
import java.util.UUID;

public interface RuleExecutionLog {
    UUID begin(UUID eventId, UUID disciplineId, String kind, Object input, UUID userId);

    void finish(UUID id, Object output, List<?> violations, long durationMs);
}
