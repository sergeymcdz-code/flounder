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

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockWritableForUpdate(UUID eventId) {
        String status = jdbc.queryForObject("select status from events where id = ? for update", String.class, eventId);
        if ("ARCHIVED".equals(status))
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "events.archived", "Архивное событие доступно только для чтения");
    }
}
