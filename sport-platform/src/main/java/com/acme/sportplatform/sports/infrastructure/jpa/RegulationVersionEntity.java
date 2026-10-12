package com.acme.sportplatform.regulations.infrastructure.jpa;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.acme.sportplatform.regulations.api.RegulationRules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "regulation_versions")
public class RegulationVersionEntity {

    @Id
    private UUID id;

    @Column(name = "regulation_template_id", nullable = false)
    private UUID regulationTemplateId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", nullable = false, columnDefinition = "jsonb")
    private RegulationRules rulesJson;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRegulationTemplateId() {
        return regulationTemplateId;
    }

    public void setRegulationTemplateId(UUID regulationTemplateId) {
        this.regulationTemplateId = regulationTemplateId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public RegulationRules getRulesJson() {
        return rulesJson;
    }

    public void setRulesJson(RegulationRules rulesJson) {
        this.rulesJson = rulesJson;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}