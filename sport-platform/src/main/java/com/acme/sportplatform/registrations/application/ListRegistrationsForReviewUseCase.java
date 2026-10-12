package com.acme.sportplatform.registrations.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
@Transactional(readOnly = true)
public class ListRegistrationsForReviewUseCase {

    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;

    public ListRegistrationsForReviewUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
    }

    public List<RegistrationResponse> execute(UUID eventId, RegistrationStatus status) {
        List<com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity> entities;

        if (eventId != null && status != null) {
            entities = registrationRepository.findByEventIdAndStatus(eventId, status.name());
        } else if (eventId != null) {
            entities = registrationRepository.findByEventId(eventId);
        } else if (status != null) {
            entities = registrationRepository.findByStatus(status.name());
        } else {
            entities = registrationRepository.findAll();
        }

        return entities.stream()
                .map(registrationMapper::toResponse)
                .toList();
    }
}