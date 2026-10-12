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

    public EventLookupService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
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