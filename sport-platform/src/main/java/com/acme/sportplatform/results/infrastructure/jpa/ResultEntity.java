package com.acme.sportplatform.results.infrastructure.jpa;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
@Entity
@Table(name = "results")
public class ResultEntity {

    @Id
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "competition_unit_entry_id", nullable = false)
    private UUID competitionUnitEntryId;

    @Column(name = "raw_value", length = 50)
    private String rawValue;

    @Column(name = "result_type", nullable = false, length = 64)
    private String resultType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "final_place")
    private Integer finalPlace;

    @Column(name = "regulation_version_id")
    private UUID regulationVersionId;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public UUID getCompetitionUnitEntryId() { return competitionUnitEntryId; }
    public void setCompetitionUnitEntryId(UUID competitionUnitEntryId) { this.competitionUnitEntryId = competitionUnitEntryId; }

    public String getRawValue() { return rawValue; }
    public void setRawValue(String rawValue) { this.rawValue = rawValue; }

    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getFinalPlace() { return finalPlace; }
    public void setFinalPlace(Integer finalPlace) { this.finalPlace = finalPlace; }

    public UUID getRegulationVersionId() { return regulationVersionId; }
    public void setRegulationVersionId(UUID regulationVersionId) { this.regulationVersionId = regulationVersionId; }

    public UUID getRecordedByUserId() { return recordedByUserId; }
    public void setRecordedByUserId(UUID recordedByUserId) { this.recordedByUserId = recordedByUserId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}