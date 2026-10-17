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
                "IMPORT".equals(entity.getSource()) ? null : entity.getParticipantUserId(),
                entity.getParticipantProfileId(),
                entity.getStatus(),
                readSnapshot(entity),
                readJson(entity.getRegistrationMeta()),
                entity.getReviewNote(),
                entity.getReviewedByUserId(),
                entity.getReviewedAt(),
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getSeedTimeMs(),
                com.acme.sportplatform.common.TimeParser.format(entity.getSeedTimeMs()),
                entity.getSource(),
                entity.getPaymentStatus(), entity.getWaitlistPosition());
    }

    private JsonNode readSnapshot(RegistrationEntity entity) {
        JsonNode snapshot = readJson(entity.getParticipantSnapshot());
        if ("IMPORT".equals(entity.getSource()) && snapshot instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            object.retain("firstName", "lastName", "middleName", "birthDate", "gender", "clubName");
        }
        return snapshot;
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