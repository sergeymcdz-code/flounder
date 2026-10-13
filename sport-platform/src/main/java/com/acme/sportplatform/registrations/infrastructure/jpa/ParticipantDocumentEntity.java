package com.acme.sportplatform.registrations.infrastructure.jpa;

import java.util.UUID;
import java.time.*;
import jakarta.persistence.*;

@Entity
@Table(name = "participant_documents")
public class ParticipantDocumentEntity {
    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "profile_id")
    private UUID profileId;
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "type")
    private String type;
    @Column(name = "file_key")
    private String fileKey;
    @Column(name = "original_name")
    private String originalName;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "size_bytes")
    private long sizeBytes;
    @Column(name = "issued_at")
    private LocalDate issuedAt;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(name = "status")
    private String status;
    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public UUID getProfileId() { return profileId; }
    public void setProfileId(UUID value) { profileId = value; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID value) { eventId = value; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public String getFileKey() { return fileKey; }
    public void setFileKey(String value) { fileKey = value; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String value) { originalName = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long value) { sizeBytes = value; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDate value) { issuedAt = value; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate value) { validUntil = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String value) { rejectReason = value; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID value) { uploadedBy = value; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime value) { uploadedAt = value; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID value) { reviewedBy = value; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime value) { reviewedAt = value; }
}
