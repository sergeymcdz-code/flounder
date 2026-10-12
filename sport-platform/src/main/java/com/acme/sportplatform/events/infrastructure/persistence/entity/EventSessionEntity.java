package com.acme.sportplatform.events.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_sessions")
public class EventSessionEntity {
    @Id private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private OffsetDateTime startsAt;

    @Column(nullable = false)
    private int breakBetweenEventsSec = 60;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID value) {
        id = value;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID value) {
        eventId = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public int getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(int value) {
        orderNo = value;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(OffsetDateTime value) {
        startsAt = value;
    }

    public int getBreakBetweenEventsSec() {
        return breakBetweenEventsSec;
    }

    public void setBreakBetweenEventsSec(int value) {
        breakBetweenEventsSec = value;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime value) {
        createdAt = value;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime value) {
        updatedAt = value;
    }
}
