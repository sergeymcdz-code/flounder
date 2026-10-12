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
            EventStatus.COMPLETED.name()
    );

    private final EventRepository eventRepository;

    public EventPublicLookupService(
            EventRepository eventRepository
    ) {
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
        EventEntity event = eventRepository
                .findByPublicSlugAndStatusIn(
                        publicSlug,
                        PUBLIC_EVENT_STATUSES
                )
                .orElseThrow(() -> new BusinessException(
                        "events.public_not_found",
                        "Public event not found"
                ));

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
                event.getPublicSlug()
        );
    }
}