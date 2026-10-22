package com.acme.sportplatform.events.application;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventPublicLookup;
import com.acme.sportplatform.events.EventPublicLookupResult;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;

@Service
@Transactional(readOnly = true)
public class EventPublicLookupService
        implements EventPublicLookup {

    private static final Set<String> PUBLIC_EVENT_STATUSES = Set.of(
            EventStatus.PUBLISHED.name(),
            EventStatus.REGISTRATION_OPEN.name(),
            EventStatus.REGISTRATION_CLOSED.name(),
            EventStatus.LIVE.name(),
            EventStatus.COMPLETED.name(),
            EventStatus.ARCHIVED.name()
    );

    private final EventRepository eventRepository;
    private final EventSessionService sessions;
    private final com.acme.sportplatform.organizations.VenueLookup venues;

    public EventPublicLookupService(EventRepository eventRepository, EventSessionService sessions, com.acme.sportplatform.organizations.VenueLookup venues) {
        this.venues = venues;
        this.sessions = sessions;
        this.eventRepository = eventRepository;
    }

    @Override
    public List<EventPublicLookupResult> listPublicEvents() {
        var events = eventRepository.findByStatusInOrderByEventStartAtAsc(PUBLIC_EVENT_STATUSES);
        var cities = venues.cities(events.stream().map(EventEntity::getVenueId).filter(java.util.Objects::nonNull).distinct().toList());
        return events.stream().map(event -> toResult(event, cities.get(event.getVenueId()))).toList();
    }

    @Override
    public EventPublicLookupResult getPublicBySlug(
            String publicSlug
    ) {
        EventEntity event =
                eventRepository
                        .findByPublicSlugAndStatusIn(publicSlug, PUBLIC_EVENT_STATUSES)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "events.public_not_found",
                                                "Публичное событие не найдено"));

        String city = event.getVenueId()==null ? null : venues.cities(List.of(event.getVenueId())).get(event.getVenueId());
        return toResult(event, city);
    }

    private EventPublicLookupResult toResult(
            EventEntity event, String city
    ) {
        return new EventPublicLookupResult(
                event.getId(),
                event.getSportId(),
                event.getTitle(),
                event.getDescription(),
                event.getRegistrationOpenAt(),
                event.getRegistrationCloseAt(),
                event.getEventStartAt(),
                event.getEventEndAt(),
                event.getStatus(),
                event.getPublicSlug(),
                sessions.views(event.getId()), event.getOrganizationId(), event.getVenueId(),
                city);
    }
}