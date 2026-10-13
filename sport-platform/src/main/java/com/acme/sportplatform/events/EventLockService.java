package com.acme.sportplatform.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Locks are held by the surrounding use-case transaction. */
@Service
public class EventLockService {
    private final JdbcTemplate jdbc;

    public EventLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForUpdate(UUID eventId) {
        jdbc.queryForObject("select id from events where id = ? for update", UUID.class, eventId);
    }
}
