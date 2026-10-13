package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.EventSessionLookup;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EventSessionLookupService implements EventSessionLookup {
    private final EventSessionRepository sessions;

    public EventSessionLookupService(EventSessionRepository sessions) {
        this.sessions = sessions;
    }

    public List<Session> findByEventId(UUID eventId) {
        return sessions.findByEventIdOrderByOrderNoAsc(eventId).stream()
                .map(
                        s ->
                                new Session(
                                        s.getId(),
                                        s.getOrderNo(),
                                        s.getStartsAt().toInstant(),
                                        s.getBreakBetweenEventsSec()))
                .toList();
    }
}
