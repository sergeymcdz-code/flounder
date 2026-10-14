package com.acme.sportplatform.events.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.api.EventResponse;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;

@Service
public class CompleteEventUseCase {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public CompleteEventUseCase(EventRepository eventRepository, EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public EventResponse execute(UUID eventId) {
        EventEntity event = eventRepository.lockById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "events.event_not_found",
                        "Событие не найдено"
                ));

        if (!EventStatus.LIVE.name().equals(event.getStatus())
                && !EventStatus.REGISTRATION_CLOSED.name().equals(event.getStatus())) {
            throw new BusinessException(
                    "events.invalid_status_transition",
                    "Завершить можно только событие в статусе LIVE или REGISTRATION_CLOSED"
            );
        }

        event.setStatus(EventStatus.COMPLETED.name());
        event.setUpdatedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        EventEntity saved = eventRepository.save(event);
        return eventMapper.toResponse(saved);
    }
}