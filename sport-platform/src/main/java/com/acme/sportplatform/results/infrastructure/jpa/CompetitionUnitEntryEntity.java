package com.acme.sportplatform.results.infrastructure.jpa;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "competition_unit_entries")
public class CompetitionUnitEntryEntity {

    @Id
    private UUID id;

    @Column(name = "competition_unit_id", nullable = false)
    private UUID competitionUnitId;

    @Column(name = "registration_id", nullable = false)
    private UUID registrationId;

    @Column(name = "lane_or_position")
    private Integer laneOrPosition;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCompetitionUnitId() { return competitionUnitId; }
    public void setCompetitionUnitId(UUID competitionUnitId) { this.competitionUnitId = competitionUnitId; }

    public UUID getRegistrationId() { return registrationId; }
    public void setRegistrationId(UUID registrationId) { this.registrationId = registrationId; }

    public Integer getLaneOrPosition() { return laneOrPosition; }
    public void setLaneOrPosition(Integer laneOrPosition) { this.laneOrPosition = laneOrPosition; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}