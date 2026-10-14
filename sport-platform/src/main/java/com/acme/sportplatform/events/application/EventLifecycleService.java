package com.acme.sportplatform.events.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.events.api.EventResponse;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(EventLifecycleService.class);
    private final EventRepository events;
    private final EventMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;

    public EventLifecycleService(EventRepository events, EventMapper mapper,
            ApplicationEventPublisher publisher, @Qualifier("eventClock") Clock clock, jakarta.persistence.EntityManager entityManager) {
        this.events = events;
        this.mapper = mapper;
        this.publisher = publisher;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public EventResponse transition(UUID id, EventStatus target) {
        var event = require(id);
        if (!EventStatus.valueOf(event.getStatus()).canTransition(target))
            throw new BusinessException("events.invalid_transition", switch (target) {
                case REGISTRATION_OPEN -> "Открыть регистрацию можно только для события в статусе PUBLISHED или REGISTRATION_CLOSED";
                case REGISTRATION_CLOSED -> "Закрыть регистрацию можно только для события в статусе REGISTRATION_OPEN";
                case ARCHIVED -> "Архивировать можно только событие в статусе COMPLETED";
                default -> "Недопустимый переход статуса события";
            });
        if (target == EventStatus.REGISTRATION_OPEN && "REGISTRATION_CLOSED".equals(event.getStatus())
                && event.getRegistrationCloseAt() != null && !event.getRegistrationCloseAt().isAfter(OffsetDateTime.now(clock)))
            throw new BusinessException("events.invalid_dates", "Сначала укажите новую дату закрытия регистрации");
        change(event, target);
        return mapper.toResponse(event);
    }

    @Transactional
    public void advanceDue(UUID id) {
        var event = require(id);
        var now = OffsetDateTime.now(clock);
        // Both deadlines can have elapsed while the application was stopped.
        if ("PUBLISHED".equals(event.getStatus()) && event.getRegistrationOpenAt() != null
                && !now.isBefore(event.getRegistrationOpenAt()))
            change(event, EventStatus.REGISTRATION_OPEN);
        if ("REGISTRATION_OPEN".equals(event.getStatus()) && event.getRegistrationCloseAt() != null
                && !now.isBefore(event.getRegistrationCloseAt()))
            change(event, EventStatus.REGISTRATION_CLOSED);
    }

    private EventEntity require(UUID id) {
        var event = events.lockById(id).orElseThrow(() ->
                new BusinessException("events.event_not_found", "Событие не найдено"));
        entityManager.refresh(event);
        return event;
    }

    private void change(EventEntity event, EventStatus target) {
        String previous = event.getStatus();
        var now = OffsetDateTime.now(clock);
        event.setStatus(target.name());
        event.setUpdatedAt(now);
        events.save(event);
        log.info("Event {} lifecycle: {} -> {}", event.getId(), previous, target);
        switch (target) {
            case REGISTRATION_OPEN -> publisher.publishEvent(new EventRegistrationOpenedEvent(event.getId(), now));
            case REGISTRATION_CLOSED -> publisher.publishEvent(new EventRegistrationClosedEvent(event.getId(), now));
            case ARCHIVED -> publisher.publishEvent(new EventArchivedEvent(event.getId(), now));
            default -> { }
        }
    }
}
