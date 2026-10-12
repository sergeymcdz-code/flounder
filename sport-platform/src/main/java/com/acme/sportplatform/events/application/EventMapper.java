package com.acme.sportplatform.events.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.events.api.EventResponse;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;

@Component
public class EventMapper {

    public EventResponse toResponse(EventEntity entity) {
        return new EventResponse(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getVenueId(),
                entity.getSportId(),
                entity.getRegulationVersionId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getRegistrationOpenAt(),
                entity.getRegistrationCloseAt(),
                entity.getEventStartAt(),
                entity.getEventEndAt(),
                entity.getStatus(),
                entity.getPublicSlug(),
                entity.getSettingsJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                List.of()
        );
    }
}