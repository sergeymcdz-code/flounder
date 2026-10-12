package com.acme.sportplatform.registrations.infrastructure.jpa;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "registrations")
public class RegistrationEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_discipline_id", nullable = false)
    private UUID eventDisciplineId;

    @Column(name = "participant_user_id", nullable = false)
    private UUID participantUserId;

    @Column(name = "participant_profile_id", nullable = false)
    private UUID participantProfileId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participant_snapshot", nullable = false, columnDefinition = "jsonb")
    private String participantSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "registration_meta", nullable = false, columnDefinition = "jsonb")
    private String registrationMeta;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

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

    public UUID getEventDisciplineId() {
        return eventDisciplineId;
    }

    public void setEventDisciplineId(UUID eventDisciplineId) {
        this.eventDisciplineId = eventDisciplineId;
    }

    public UUID getParticipantUserId() {
        return participantUserId;
    }

    public void setParticipantUserId(UUID participantUserId) {
        this.participantUserId = participantUserId;
    }

    public UUID getParticipantProfileId() {
        return participantProfileId;
    }

    public void setParticipantProfileId(UUID participantProfileId) {
        this.participantProfileId = participantProfileId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getParticipantSnapshot() {
        return participantSnapshot;
    }

    public void setParticipantSnapshot(String participantSnapshot) {
        this.participantSnapshot = participantSnapshot;
    }

    public String getRegistrationMeta() {
        return registrationMeta;
    }

    public void setRegistrationMeta(String registrationMeta) {
        this.registrationMeta = registrationMeta;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
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

    public UUID getReviewedByUserId() { 
        return reviewedByUserId; 
    }

    public void setReviewedByUserId(UUID reviewedByUserId) { 
        this.reviewedByUserId = reviewedByUserId; 
    }

    public OffsetDateTime getReviewedAt() { 
        return reviewedAt; 
    }
    
    public void setReviewedAt(OffsetDateTime reviewedAt) { 
        this.reviewedAt = reviewedAt; 
    }

}