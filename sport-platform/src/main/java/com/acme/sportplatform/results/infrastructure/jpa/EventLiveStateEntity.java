package com.acme.sportplatform.results.infrastructure.jpa;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_live_state")
public class EventLiveStateEntity {
    @Id private UUID eventId;
    private UUID currentUnitId;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public void setCurrentUnitId(UUID id) {
        currentUnitId = id;
    }

    public void setUpdatedAt(OffsetDateTime value) {
        updatedAt = value;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getCurrentUnitId() {
        return currentUnitId;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
