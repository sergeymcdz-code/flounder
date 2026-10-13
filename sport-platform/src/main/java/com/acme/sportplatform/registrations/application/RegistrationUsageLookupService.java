package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.competition.RegistrationUsageLookup;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RegistrationUsageLookupService implements RegistrationUsageLookup {
    private final RegistrationRepository repository;

    public RegistrationUsageLookupService(RegistrationRepository repository) {
        this.repository = repository;
    }

    public boolean isInUse(UUID id) {
        return repository.existsByEventDisciplineId(id);
    }
}
