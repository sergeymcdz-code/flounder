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
public class OpenEventRegistrationUseCase {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public OpenEventRegistrationUseCase(EventRepository eventRepository, EventMapper eventMapper) {
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

        if (!EventStatus.PUBLISHED.name().equals(event.getStatus())) {
            throw new BusinessException(
                    "events.invalid_status_transition",
                    "Only published event can be moved to registration_open"
            );
        }

        event.setStatus(EventStatus.REGISTRATION_OPEN.name());
        EventEntity saved = eventRepository.save(event);
        return eventMapper.toResponse(saved);
    }
}