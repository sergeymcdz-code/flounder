package com.acme.sportplatform.publication.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import com.acme.sportplatform.publication.api.PublicResponses.Session;

/** Temporary read mapping until T03 provides a session lookup. */
@Entity(name = "PublicationEventSession")
@Table(name = "event_sessions")
@Immutable
public class EventSessionReadEntity {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(nullable = false) private String name;
    @Column(name = "order_no", nullable = false) private int orderNo;
    @Column(name = "starts_at", nullable = false) private OffsetDateTime startsAt;
    @Column(name = "break_between_events_sec", nullable = false) private int breakBetweenEventsSec;
    protected EventSessionReadEntity() {}
    public Session toResponse() { return new Session(id, name, orderNo, startsAt, breakBetweenEventsSec); }
}
