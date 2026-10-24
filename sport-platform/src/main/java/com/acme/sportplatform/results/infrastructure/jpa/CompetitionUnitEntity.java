package com.acme.sportplatform.results.infrastructure.jpa;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "competition_units")
public class CompetitionUnitEntity {

    @Id
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "event_discipline_id", nullable = false)
    private UUID eventDisciplineId;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private boolean official;

    public boolean isOfficial() { return official; }
    public void setOfficial(boolean official) { this.official = official; }

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "actual_started_at")
    private OffsetDateTime actualStartedAt;

    @Column(name = "actual_finished_at")
    private OffsetDateTime actualFinishedAt;

    public OffsetDateTime getActualStartedAt() { return actualStartedAt; }
    public void setActualStartedAt(OffsetDateTime value) { actualStartedAt = value; }
    public OffsetDateTime getActualFinishedAt() { return actualFinishedAt; }
    public void setActualFinishedAt(OffsetDateTime value) { actualFinishedAt = value; }

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(length = 64)
    private String ageGroupCode;

    @Column private Integer heatDurationSec;

    @Column private UUID generationRunId;

    public String getAgeGroupCode() {
        return ageGroupCode;
    }

    public void setAgeGroupCode(String value) {
        this.ageGroupCode = value;
    }

    public Integer getHeatDurationSec() {
        return heatDurationSec;
    }

    public void setHeatDurationSec(Integer value) {
        this.heatDurationSec = value;
    }

    public UUID getGenerationRunId() {
        return generationRunId;
    }

    public void setGenerationRunId(UUID value) {
        this.generationRunId = value;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }


    public UUID getEventDisciplineId() { return eventDisciplineId; }
    public void setEventDisciplineId(UUID eventDisciplineId) { this.eventDisciplineId = eventDisciplineId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}