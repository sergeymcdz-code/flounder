package com.acme.sportplatform.results.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rule_execution_logs")
public class RuleExecutionLogEntity {
    @Id private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    private UUID eventDisciplineId;

    @Column(nullable = false, length = 32)
    private String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode inputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode outputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode violationsJson;

    @Column(nullable = false)
    private int durationMs;

    private UUID executedBy;

    @Column(nullable = false)
    private OffsetDateTime executedAt;

    protected RuleExecutionLogEntity() {}

    public RuleExecutionLogEntity(
            UUID eventId,
            UUID disciplineId,
            UUID userId,
            JsonNode before,
            JsonNode after,
            int durationMs) {
        id = UUID.randomUUID();
        this.eventId = eventId;
        eventDisciplineId = disciplineId;
        kind = "RANKING";
        executedBy = userId;
        inputJson = before;
        outputJson = after;
        violationsJson = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        this.durationMs = durationMs;
        executedAt = OffsetDateTime.now();
    }

    public JsonNode getInputJson() {
        return inputJson;
    }

    public JsonNode getOutputJson() {
        return outputJson;
    }

    public String getKind() {
        return kind;
    }
}
