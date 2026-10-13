package com.acme.sportplatform.files.infrastructure.jpa;

import java.util.UUID;
import java.time.*;
import jakarta.persistence.*;

@Entity
@Table(name = "file_access_log")
public class FileAccessLogEntity {
    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "file_key")
    private String fileKey;
    @Column(name = "document_id")
    private UUID documentId;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "action")
    private String action;
    @Column(name = "at")
    private OffsetDateTime at;
    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getFileKey() { return fileKey; }
    public void setFileKey(String value) { fileKey = value; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID value) { documentId = value; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID value) { userId = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    public OffsetDateTime getAt() { return at; }
    public void setAt(OffsetDateTime value) { at = value; }
}
