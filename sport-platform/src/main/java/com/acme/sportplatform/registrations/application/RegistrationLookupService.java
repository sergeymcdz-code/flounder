package com.acme.sportplatform.registrations.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
@Transactional(readOnly = true)
public class RegistrationLookupService implements RegistrationLookup {

    private final RegistrationRepository registrationRepository;

    public RegistrationLookupService(
            RegistrationRepository registrationRepository
    ) {
        this.registrationRepository = registrationRepository;
    }

    @Override
    public RegistrationLookupResult getById(UUID registrationId) {
        return findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Registration not found: " + registrationId
                ));
    }

    @Override
    public Optional<RegistrationLookupResult> findById(
            UUID registrationId
    ) {
        return registrationRepository.findById(registrationId)
                .map(this::toResult);
    }

    @Override
    public List<RegistrationLookupResult> findByIdIn(
            Collection<UUID> registrationIds
    ) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return List.of();
        }

        return registrationRepository.findByIdIn(
                        List.copyOf(registrationIds)
                )
                .stream()
                .map(this::toResult)
                .toList();
    }

    private RegistrationLookupResult toResult(
            RegistrationEntity registration
    ) {
        return new RegistrationLookupResult(
                registration.getId(),
                registration.getParticipantUserId(),
                registration.getEventDisciplineId(),
                registration.getStatus(),
                registration.getParticipantSnapshot()
        );
    }
}