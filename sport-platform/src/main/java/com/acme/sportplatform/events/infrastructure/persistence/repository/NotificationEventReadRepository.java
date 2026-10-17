package com.acme.sportplatform.events.infrastructure.persistence.repository;

import com.acme.sportplatform.events.NotificationEventLookup.Context;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationEventReadRepository {
    private final JdbcTemplate jdbc;

    public NotificationEventReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Context> activeContexts() {
        return jdbc.query(
                """
                select e.id, e.organization_id, e.title, e.event_start_at, e.public_slug,
                       e.venue_id, coalesce(v.timezone, 'UTC') timezone
                from events e left join venues v on v.id = e.venue_id
                where e.status in ('PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'LIVE')
                order by e.event_start_at, e.id
                """,
                (row, n) ->
                        new Context(
                                row.getObject("id", UUID.class),
                                row.getObject("organization_id", UUID.class),
                                row.getString("title"),
                                row.getObject("event_start_at", OffsetDateTime.class),
                                row.getString("public_slug"),
                                row.getObject("venue_id", UUID.class),
                                row.getString("timezone")));
    }
}
