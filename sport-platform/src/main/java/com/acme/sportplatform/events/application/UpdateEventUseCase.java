package com.acme.sportplatform.events.application;

import com.acme.sportplatform.common.RequestValidation;
import com.acme.sportplatform.events.api.*;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.acme.sportplatform.events.RegistrationCapacityPromotionLookup;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateEventUseCase {
    private final RegistrationCapacityPromotionLookup capacity;
    private final EventRepository events;
    private final EventMapper mapper;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;
    public UpdateEventUseCase(EventRepository events, EventMapper mapper, @Qualifier("eventClock") Clock clock, jakarta.persistence.EntityManager entityManager, RegistrationCapacityPromotionLookup capacity) {
        this.capacity = capacity;
        this.events = events; this.mapper = mapper; this.clock = clock; this.entityManager = entityManager;
    }

    @Transactional
    public EventResponse execute(UUID id, PatchEventRequest request, Set<String> fields) {
        var event = events.lockById(id).orElseThrow(() -> new com.acme.sportplatform.common.exception.BusinessException("events.event_not_found", "Событие не найдено"));
        entityManager.refresh(event);
        if ("ARCHIVED".equals(event.getStatus()))
            throw new com.acme.sportplatform.common.exception.BusinessException("events.archived", "Архивное событие доступно только для чтения");
        Integer previousLimit = event.getMaxParticipants();
        if (fields.contains("maxParticipants")) event.setMaxParticipants(request.maxParticipants());
        if (fields.contains("registrationOpenAt")) event.setRegistrationOpenAt(request.registrationOpenAt());
        if (fields.contains("registrationCloseAt")) event.setRegistrationCloseAt(request.registrationCloseAt());
        if (event.getRegistrationOpenAt() != null && event.getRegistrationCloseAt() != null
                && event.getRegistrationCloseAt().isBefore(event.getRegistrationOpenAt()))
            throw RequestValidation.field("events.invalid_dates", "registrationCloseAt", "Закрытие регистрации не может быть раньше открытия");
        event.setUpdatedAt(OffsetDateTime.now(clock));
        events.saveAndFlush(event);
        if (previousLimit != null && (event.getMaxParticipants() == null || event.getMaxParticipants() > previousLimit))
            capacity.promote(id);
        return mapper.toResponse(event);
    }
}
