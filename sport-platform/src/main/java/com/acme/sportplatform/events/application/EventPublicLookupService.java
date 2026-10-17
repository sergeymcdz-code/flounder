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
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public EventPublicLookupService(EventRepository eventRepository, EventSessionService sessions, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.json = json;
        this.sessions = sessions;
        this.eventRepository = eventRepository;
    }

    @Override
    public List<EventPublicLookupResult> listPublicEvents() {
        return eventRepository
                .findByStatusInOrderByEventStartAtAsc(
                        PUBLIC_EVENT_STATUSES
                )
                .stream()
                .map(this::toResult)
                .toList();
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

        return toResult(event);
    }

    private EventPublicLookupResult toResult(
            EventEntity event
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
                sessions.views(event.getId()), publicPayment(event));
    }
    private com.acme.sportplatform.events.EventPaymentSettings publicPayment(EventEntity event) {
        var payment = com.acme.sportplatform.events.EventPaymentSettings.fromJson(event.getSettingsJson(), json);
        return payment.enabled() ? payment : null;
    }
}