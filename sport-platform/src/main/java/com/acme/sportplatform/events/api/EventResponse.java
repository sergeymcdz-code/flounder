package com.acme.sportplatform.events.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID organizationId,
        UUID venueId,
        UUID sportId,
        UUID regulationVersionId,
        String title,
        String description,
        OffsetDateTime registrationOpenAt,
        OffsetDateTime registrationCloseAt,
        OffsetDateTime eventStartAt,
        OffsetDateTime eventEndAt,
        String status,
        String publicSlug,
        String settingsJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<DisciplineResponse> disciplines,
        List<com.acme.sportplatform.events.EventSessionView> sessions,
        Integer maxParticipants) {
    public EventResponse(
        UUID id,
        UUID organizationId,
        UUID venueId,
        UUID sportId,
        UUID regulationVersionId,
        String title,
        String description,
        OffsetDateTime registrationOpenAt,
        OffsetDateTime registrationCloseAt,
        OffsetDateTime eventStartAt,
        OffsetDateTime eventEndAt,
        String status,
        String publicSlug,
        String settingsJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<DisciplineResponse> disciplines,
        List<com.acme.sportplatform.events.EventSessionView> sessions) {
        this(id, organizationId, venueId, sportId, regulationVersionId, title, description, registrationOpenAt, registrationCloseAt, eventStartAt, eventEndAt, status, publicSlug, settingsJson, createdAt, updatedAt, disciplines, sessions, null);
    }

    public record DisciplineResponse(
            UUID id,
            String code,
            String name,
            String competitionFormat,
            String unitType,
            String resultType,
            String rankingStrategy,
            Integer participantLimit,
            BigDecimal entryFeeAmount,
            String entryFeeCurrency,
            String settingsJson
    ) {
    }
    @com.fasterxml.jackson.annotation.JsonProperty("payment")
    public com.acme.sportplatform.events.EventPaymentSettings payment() {
        return com.acme.sportplatform.events.EventPaymentSettings.fromJson(settingsJson, new com.fasterxml.jackson.databind.ObjectMapper());
    }
}