package com.acme.sportplatform.scheduling.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class RuleExecutionLog {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RuleExecutionLog(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public UUID begin(UUID eventId, UUID disciplineId, String kind, Object input, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into rule_execution_logs(id,event_id,event_discipline_id,kind,input_json,output_json,duration_ms,executed_by)
                values(?,?,?,?,?::jsonb,'{}'::jsonb,0,?)
                """,
                id,
                eventId,
                disciplineId,
                kind,
                encode(input),
                userId);
        return id;
    }

    public void finish(UUID id, Object output, List<?> violations, long durationMs) {
        jdbc.update(
                "update rule_execution_logs set"
                    + " output_json=?::jsonb,violations_json=?::jsonb,duration_ms=? where id=?",
                encode(output),
                encode(violations),
                Math.toIntExact(durationMs),
                id);
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сохранить журнал правил", ex);
        }
    }
}
