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
    private final com.acme.sportplatform.organizations.OrganizationManagementAccess organizations;

    public EventLookupService(EventRepository eventRepository,
            com.acme.sportplatform.organizations.OrganizationManagementAccess organizations) {
        this.eventRepository = eventRepository;
        this.organizations = organizations;
    }

    @Override
    public java.util.Set<UUID> accessibleEventIds(UUID userId) {
        return eventRepository.findByOrganizationIdInOrCreatedByUserIdOrderByEventStartAtDesc(
                        organizations.organizationIdsOf(userId), userId).stream()
                .map(EventEntity::getId).collect(java.util.stream.Collectors.toSet());
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
                event.getStatus()
        );
    }
}