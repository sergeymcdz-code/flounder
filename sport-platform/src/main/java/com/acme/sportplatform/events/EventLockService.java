package com.acme.sportplatform.events;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Locks are held by the surrounding use-case transaction; FK inserts remain compatible. */
@Service
public class EventLockService {
    private static final String LOCK_SQL = "select status from events where id = ? for no key update";
    private final JdbcTemplate jdbc;

    public EventLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForUpdate(UUID eventId) {
        jdbc.queryForObject(LOCK_SQL, String.class, eventId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockWritableForUpdate(UUID eventId) {
        String status = jdbc.queryForObject(LOCK_SQL, String.class, eventId);
        if ("ARCHIVED".equals(status))
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "events.archived", "Архивное событие доступно только для чтения");
    }
}
