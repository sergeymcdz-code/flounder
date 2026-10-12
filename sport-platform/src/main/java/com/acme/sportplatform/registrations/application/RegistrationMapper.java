package com.acme.sportplatform.registrations.application;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RegistrationMapper {

    private final ObjectMapper objectMapper;

    public RegistrationMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RegistrationResponse toResponse(RegistrationEntity entity) {
        return new RegistrationResponse(
                entity.getId(),
                entity.getEventId(),
                entity.getEventDisciplineId(),
                entity.getParticipantUserId(),
                entity.getParticipantProfileId(),
                entity.getStatus(),
                readJson(entity.getParticipantSnapshot()),
                readJson(entity.getRegistrationMeta()),
                entity.getReviewNote(),
                entity.getReviewedByUserId(),
                entity.getReviewedAt(),
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored registration JSON is invalid",
                    exception
            );
        }
    }
}