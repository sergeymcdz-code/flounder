package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.EventSessionLookup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EventSessionLookupService implements EventSessionLookup {
    private final JdbcTemplate jdbc;

    public EventSessionLookupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Session> findByEventId(UUID eventId) {
        return jdbc.query(
                "select * from event_sessions where event_id = ? order by order_no",
                (rs, n) ->
                        new Session(
                                rs.getObject("id", UUID.class),
                                rs.getInt("order_no"),
                                rs.getTimestamp("starts_at").toInstant(),
                                rs.getInt("break_between_events_sec")),
                eventId);
    }
}
