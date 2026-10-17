package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
public class RegistrationMapper {

    private final RegistrationFeeService fees;
    private final com.acme.sportplatform.identity.UserLookup users;
    private final ObjectMapper objectMapper;

    public RegistrationMapper(
            ObjectMapper objectMapper,
            RegistrationFeeService fees,
            com.acme.sportplatform.identity.UserLookup users) {
        this.fees = fees;
        this.users = users;

        this.objectMapper = objectMapper;
    }

    public RegistrationResponse toResponse(RegistrationEntity entity) {
        return toResponses(
                        java.util.List.of(entity),
                        fees.dueDaysFor(java.util.List.of(entity.getEventId())))
                .getFirst();
    }

    public java.util.List<RegistrationResponse> toResponses(
            java.util.List<RegistrationEntity> entities,
            java.util.Map<java.util.UUID, Integer> dueDays) {
        var actorIds =
                entities.stream()
                        .map(RegistrationEntity::getPaidByUserId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
        var actors =
                actorIds.isEmpty()
                        ? java.util.Map
                                .<java.util.UUID, com.acme.sportplatform.identity.UserLookup.User>
                                        of()
                        : users.findAllByIds(actorIds).stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                com.acme.sportplatform.identity.UserLookup.User::id,
                                                java.util.function.Function.identity()));
        return entities.stream()
                .map(
                        entity ->
                                toResponse(
                                        entity,
                                        dueDays.getOrDefault(entity.getEventId(), 3),
                                        actors))
                .toList();
    }

    private RegistrationResponse toResponse(
            RegistrationEntity entity,
            int dueDays,
            java.util.Map<java.util.UUID, com.acme.sportplatform.identity.UserLookup.User> actors) {
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
                entity.getUpdatedAt(),
                entity.getSeedTimeMs(),
                com.acme.sportplatform.common.TimeParser.format(entity.getSeedTimeMs()),
                entity.getSource(),
                entity.getPaymentStatus(),
                java.util.Map.of(),
                java.util.List.of(),
                entity.getWaitlistPosition(),
                entity.getFeeAmount(),
                entity.getFeeCurrency(),
                entity.getPaidAmount(),
                entity.getPaidAt(),
                paidBy(entity, actors),
                entity.getPaymentMethod(),
                entity.getPaymentReference(),
                entity.getPaymentComment(),
                entity.getReviewedAt() == null ? null : entity.getReviewedAt().plusDays(dueDays));
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored registration JSON is invalid", exception);
        }
    }

    private RegistrationResponse.PaidBy paidBy(
            RegistrationEntity entity,
            java.util.Map<java.util.UUID, com.acme.sportplatform.identity.UserLookup.User> actors) {
        if (entity.getPaidByUserId() == null) return null;
        String name =
                java.util.Optional.ofNullable(actors.get(entity.getPaidByUserId()))
                        .map(
                                u ->
                                        java.util.stream.Stream.of(u.firstName(), u.lastName())
                                                .filter(java.util.Objects::nonNull)
                                                .collect(java.util.stream.Collectors.joining(" ")))
                        .orElse("");
        return new RegistrationResponse.PaidBy(entity.getPaidByUserId(), name);
    }
}
