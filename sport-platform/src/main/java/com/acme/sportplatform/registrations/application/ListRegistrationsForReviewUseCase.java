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

    private final DocumentSummaryService summaries;
    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final com.acme.sportplatform.events.EventLookup events;

    public ListRegistrationsForReviewUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            com.acme.sportplatform.events.EventLookup events, DocumentSummaryService summaries
    ) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
        this.events = events;
        this.summaries = summaries;
    }

    public List<RegistrationResponse> execute(UUID eventId, RegistrationStatus status,
            com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        List<com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity> entities;

        if (eventId != null && status != null) {
            entities = registrationRepository.findByEventIdAndStatus(eventId, status.name());
        } else if (eventId != null) {
            entities = registrationRepository.findByEventId(eventId);
        } else if (!principal.isPlatformAdmin()) {
            var eventIds = events.accessibleEventIds(principal.getUserId());
            if (eventIds.isEmpty()) return List.of();
            entities = status == null ? registrationRepository.findByEventIdIn(eventIds)
                    : registrationRepository.findByEventIdInAndStatus(eventIds, status.name());
        } else if (status != null) {
            entities = registrationRepository.findByStatus(status.name());
        } else {
            entities = registrationRepository.findAll();
        }

        return summaries.enrich(entities.stream()
                .map(registrationMapper::toResponse)
                .toList());
    }
}