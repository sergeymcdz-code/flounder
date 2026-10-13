package com.acme.sportplatform.results.application;

import com.acme.sportplatform.results.RuleExecutionLog;
import com.acme.sportplatform.results.infrastructure.jpa.RuleExecutionLogEntity;
import com.acme.sportplatform.results.infrastructure.jpa.RuleExecutionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RuleExecutionLogService implements RuleExecutionLog {
    private final RuleExecutionLogRepository logs;
    private final ObjectMapper json;

    public RuleExecutionLogService(RuleExecutionLogRepository logs, ObjectMapper json) {
        this.logs = logs;
        this.json = json;
    }

    public UUID begin(UUID eventId, UUID disciplineId, String kind, Object input, UUID userId) {
        UUID id = UUID.randomUUID();
        logs.saveAndFlush(
                new RuleExecutionLogEntity(
                        id,
                        eventId,
                        disciplineId,
                        kind,
                        userId,
                        json.valueToTree(input),
                        json.createObjectNode(),
                        json.createArrayNode(),
                        0));
        return id;
    }

    public void finish(UUID id, Object output, List<?> violations, long durationMs) {
        var log =
                logs.findById(id)
                        .orElseThrow(() -> new IllegalStateException("Прогон правил не найден"));
        log.complete(
                json.valueToTree(output),
                json.valueToTree(violations),
                Math.toIntExact(durationMs));
        logs.saveAndFlush(log);
    }
}
