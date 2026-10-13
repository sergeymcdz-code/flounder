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
    public java.util.Map<UUID, java.time.LocalDate> startDates(java.util.Collection<UUID> eventIds) {
        return eventRepository.findAllById(eventIds).stream().collect(java.util.stream.Collectors.toMap(
                EventEntity::getId, event -> event.getEventStartAt().toLocalDate()));
    }

    @Override
    public java.util.Set<UUID> accessibleEventIds(UUID userId) {
        return eventRepository.findByOrganizationIdInOrCreatedByUserIdOrderByEventStartAtDesc(
                        organizations.organizationIdsOf(userId), userId).stream()
                .map(EventEntity::getId).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public com.acme.sportplatform.events.EventProtocolHeader protocolHeader(UUID eventId) {
        var event = eventRepository.findById(eventId).orElseThrow(() ->
                new com.acme.sportplatform.common.exception.BusinessException(
                        "events.event_not_found", "Событие не найдено"));
        return new com.acme.sportplatform.events.EventProtocolHeader(event.getId(), event.getTitle(),
                event.getOrganizationId(), event.getVenueId(), event.getEventStartAt(), event.getEventEndAt(),
                event.getPublicSlug());
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
                event.getStatus(), event.getRegistrationOpenAt(), event.getRegistrationCloseAt(),
                sessions.views(eventId), event.getVenueId(), event.getCreatedByUserId(), event.getSettingsJson(),
                event.getPublicSlug()
        );
    }
}
