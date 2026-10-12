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
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "events.event_not_found",
                        "Event not found"
                ));

        if (!EventStatus.LIVE.name().equals(event.getStatus())
                && !EventStatus.REGISTRATION_CLOSED.name().equals(event.getStatus())) {
            throw new BusinessException(
                    "events.invalid_status_transition",
                    "Only live or registration_closed event can be completed"
            );
        }

        event.setStatus(EventStatus.COMPLETED.name());
        EventEntity saved = eventRepository.save(event);
        return eventMapper.toResponse(saved);
    }
}