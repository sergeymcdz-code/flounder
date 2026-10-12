package com.acme.sportplatform.events.infrastructure.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "events")
public class EventEntity {

    @Id
    private UUID id;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID venueId;

    @Column(nullable = false)
    private UUID sportId;

    @Column(nullable = false)
    private UUID regulationVersionId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private OffsetDateTime registrationOpenAt;
    private OffsetDateTime registrationCloseAt;

    @Column(nullable = false)
    private OffsetDateTime eventStartAt;

    @Column(nullable = false)
    private OffsetDateTime eventEndAt;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, unique = true)
    private String publicSlug;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String settingsJson = "{}";

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public void setVenueId(UUID venueId) {
        this.venueId = venueId;
    }

    public UUID getSportId() {
        return sportId;
    }

    public void setSportId(UUID sportId) {
        this.sportId = sportId;
    }

    public UUID getRegulationVersionId() {
        return regulationVersionId;
    }

    public void setRegulationVersionId(UUID regulationVersionId) {
        this.regulationVersionId = regulationVersionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getRegistrationOpenAt() {
        return registrationOpenAt;
    }

    public void setRegistrationOpenAt(OffsetDateTime registrationOpenAt) {
        this.registrationOpenAt = registrationOpenAt;
    }

    public OffsetDateTime getRegistrationCloseAt() {
        return registrationCloseAt;
    }

    public void setRegistrationCloseAt(OffsetDateTime registrationCloseAt) {
        this.registrationCloseAt = registrationCloseAt;
    }

    public OffsetDateTime getEventStartAt() {
        return eventStartAt;
    }

    public void setEventStartAt(OffsetDateTime eventStartAt) {
        this.eventStartAt = eventStartAt;
    }

    public OffsetDateTime getEventEndAt() {
        return eventEndAt;
    }

    public void setEventEndAt(OffsetDateTime eventEndAt) {
        this.eventEndAt = eventEndAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPublicSlug() {
        return publicSlug;
    }

    public void setPublicSlug(String publicSlug) {
        this.publicSlug = publicSlug;
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