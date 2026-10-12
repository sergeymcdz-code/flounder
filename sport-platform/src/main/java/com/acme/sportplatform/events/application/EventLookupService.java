package com.acme.sportplatform.events.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventLookupResult;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;

@Service
@Transactional(readOnly = true)
public class EventLookupService implements EventLookup {

    private final EventRepository eventRepository;
    private final EventSessionService sessions;
    private final com.acme.sportplatform.organizations.OrganizationManagementAccess organizations;

    public EventLookupService(EventRepository eventRepository,
            com.acme.sportplatform.organizations.OrganizationManagementAccess organizations, EventSessionService sessions) {
        this.eventRepository = eventRepository;
        this.organizations = organizations; this.sessions=sessions;
    }

    @Override
    public java.util.Set<UUID> accessibleEventIds(UUID userId) {
        return eventRepository.findByOrganizationIdInOrCreatedByUserIdOrderByEventStartAtDesc(
                        organizations.organizationIdsOf(userId), userId).stream()
                .map(EventEntity::getId).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public boolean isCreatedBy(UUID eventId, UUID userId) {
        return eventRepository.findById(eventId)
                .map(event -> userId.equals(event.getCreatedByUserId())).orElse(false);
    }

    @Override
    public EventLookupResult getById(UUID eventId) {
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Event not found: " + eventId
                ));

        return new EventLookupResult(
                event.getId(),
                event.getSportId(),
                event.getStatus(), event.getRegistrationOpenAt(), event.getRegistrationCloseAt(), sessions.views(eventId), event.getVenueId(), event.getCreatedByUserId(), event.getSettingsJson()
        );
    }
}