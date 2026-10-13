package com.acme.sportplatform.results.application;

import com.acme.sportplatform.competition.CompetitionUnitUsageLookup;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CompetitionUnitUsageLookupService implements CompetitionUnitUsageLookup {
    private final CompetitionUnitRepository repository;

    public CompetitionUnitUsageLookupService(CompetitionUnitRepository repository) {
        this.repository = repository;
    }

    public boolean isInUse(UUID id) {
        return repository.existsByEventDisciplineId(id);
    }
}
