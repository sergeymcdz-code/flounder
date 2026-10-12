package com.acme.sportplatform.competition.infrastructure.jpa;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "event_disciplines")
public class EventDisciplineEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "discipline_template_id", nullable = false)
    private UUID disciplineTemplateId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "competition_format", nullable = false, length = 64)
    private String competitionFormat;

    @Column(name = "unit_type", nullable = false, length = 64)
    private String unitType;

    @Column(name = "result_type", nullable = false, length = 64)
    private String resultType;

    @Column(name = "ranking_strategy", nullable = false, length = 64)
    private String rankingStrategy;

    @Column(name = "participant_limit")
    private Integer participantLimit;

    @Column(name = "entry_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal entryFeeAmount = BigDecimal.ZERO;

    @Column(name = "entry_fee_currency", nullable = false, length = 3)
    private String entryFeeCurrency = "RUB";

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_json", nullable = false, columnDefinition = "jsonb")
    private String settingsJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(length = 16)
    private String stroke;

    @Column(length = 1)
    private String gender;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "age_groups_json", nullable = false, columnDefinition = "jsonb")
    private java.util.List<com.acme.sportplatform.competition.AgeGroup> ageGroups =
            java.util.List.of();

    @Column(nullable = false, length = 16)
    private String seedingMode = "BY_AGE_GROUP";

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "order_in_session")
    private Integer orderInSession;

    @Column(name = "heat_duration_sec")
    private Integer heatDurationSec;

    public Integer getDistanceM() {
        return distanceM;
    }

    public void setDistanceM(Integer value) {
        this.distanceM = value;
    }

    public String getStroke() {
        return stroke;
    }

    public void setStroke(String value) {
        this.stroke = value;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String value) {
        this.gender = value;
    }

    public java.util.List<com.acme.sportplatform.competition.AgeGroup> getAgeGroups() {
        return ageGroups;
    }

    public void setAgeGroups(java.util.List<com.acme.sportplatform.competition.AgeGroup> value) {
        this.ageGroups = value;
    }

    public String getSeedingMode() {
        return seedingMode;
    }

    public void setSeedingMode(String value) {
        this.seedingMode = value;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID value) {
        this.sessionId = value;
    }

    public Integer getOrderInSession() {
        return orderInSession;
    }

    public void setOrderInSession(Integer value) {
        this.orderInSession = value;
    }

    public Integer getHeatDurationSec() {
        return heatDurationSec;
    }

    public void setHeatDurationSec(Integer value) {
        this.heatDurationSec = value;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getDisciplineTemplateId() {
        return disciplineTemplateId;
    }

    public void setDisciplineTemplateId(UUID disciplineTemplateId) {
        this.disciplineTemplateId = disciplineTemplateId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompetitionFormat() {
        return competitionFormat;
    }

    public void setCompetitionFormat(String competitionFormat) {
        this.competitionFormat = competitionFormat;
    }

    public String getUnitType() {
        return unitType;
    }

    public void setUnitType(String unitType) {
        this.unitType = unitType;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public String getRankingStrategy() {
        return rankingStrategy;
    }

    public void setRankingStrategy(String rankingStrategy) {
        this.rankingStrategy = rankingStrategy;
    }

    public Integer getParticipantLimit() {
        return participantLimit;
    }

    public void setParticipantLimit(Integer participantLimit) {
        this.participantLimit = participantLimit;
    }

    public BigDecimal getEntryFeeAmount() {
    return entryFeeAmount;
    }

    public void setEntryFeeAmount(BigDecimal entryFeeAmount) {
        this.entryFeeAmount = entryFeeAmount;
    }

    public String getEntryFeeCurrency() {
        return entryFeeCurrency;
    }

    public void setEntryFeeCurrency(String entryFeeCurrency) {
        this.entryFeeCurrency = entryFeeCurrency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSettingsJson() {
        return settingsJson;
    }

    public void setSettingsJson(String settingsJson) {
        this.settingsJson = settingsJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}