package com.acme.sportplatform.competition.application;

import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.SessionUsageLookup;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionUsageLookupService implements SessionUsageLookup {
    private final EventDisciplineRepository repository;

    public SessionUsageLookupService(EventDisciplineRepository repository) {
        this.repository = repository;
    }

    public boolean isInUse(UUID id) {
        return repository.existsBySessionId(id);
    }
}
