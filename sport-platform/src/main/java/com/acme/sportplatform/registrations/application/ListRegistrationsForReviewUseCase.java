package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListRegistrationsForReviewUseCase {

    private final DocumentSummaryService summaries;
    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final RegistrationFeeService fees;
    private final com.acme.sportplatform.events.EventLookup events;

    public ListRegistrationsForReviewUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            com.acme.sportplatform.events.EventLookup events,
            DocumentSummaryService summaries,
            RegistrationFeeService fees) {
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
        this.events = events;
        this.summaries = summaries;
        this.fees = fees;
    }

    public List<RegistrationResponse> execute(
            UUID eventId,
            RegistrationStatus status,
            com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        List<com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity> entities;

        if (eventId != null && status != null) {
            entities = registrationRepository.findByEventIdAndStatus(eventId, status.name());
        } else if (eventId != null) {
            entities = registrationRepository.findByEventId(eventId);
        } else if (!principal.isPlatformAdmin()) {
            var eventIds = events.accessibleEventIds(principal.getUserId());
            if (eventIds.isEmpty()) return List.of();
            entities =
                    status == null
                            ? registrationRepository.findByEventIdIn(eventIds)
                            : registrationRepository.findByEventIdInAndStatus(
                                    eventIds, status.name());
        } else if (status != null) {
            entities = registrationRepository.findByStatus(status.name());
        } else {
            entities = registrationRepository.findAll();
        }

        return summaries.enrich(
                registrationMapper.toResponses(
                        entities,
                        fees.dueDaysFor(
                                entities.stream()
                                        .map(r -> r.getEventId())
                                        .collect(java.util.stream.Collectors.toSet()))));
    }
}
