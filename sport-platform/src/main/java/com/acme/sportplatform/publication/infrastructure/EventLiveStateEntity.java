package com.acme.sportplatform.publication.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;

@Entity(name = "PublicationEventLiveState")
@Table(name = "event_live_state")
public class EventLiveStateEntity {
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "current_unit_id") private UUID currentUnitId;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    protected EventLiveStateEntity() {}
    public EventLiveStateEntity(UUID eventId) { this.eventId = eventId; }
    public UUID getCurrentUnitId() { return currentUnitId; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void update(UUID unitId, OffsetDateTime now) { currentUnitId = unitId; updatedAt = now; }
}
