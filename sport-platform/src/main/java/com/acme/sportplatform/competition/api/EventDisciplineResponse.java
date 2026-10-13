package com.acme.sportplatform.competition.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EventDisciplineResponse(
        UUID id,
        UUID eventId,
        UUID disciplineTemplateId,
        String code,
        String name,
        String competitionFormat,
        String unitType,
        String resultType,
        String rankingStrategy,
        Integer participantLimit,
        BigDecimal entryFeeAmount,
        String entryFeeCurrency,
        String status,
        String settingsJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Integer distanceM,
        String stroke,
        String gender,
        java.util.List<com.acme.sportplatform.competition.AgeGroup> ageGroups,
        String seedingMode,
        UUID sessionId,
        Integer orderInSession,
        Integer heatDurationSec,
        Integer maxParticipants,
        java.util.Map<String, Integer> limitsByAgeGroup) {
    public EventDisciplineResponse(
        UUID id,
        UUID eventId,
        UUID disciplineTemplateId,
        String code,
        String name,
        String competitionFormat,
        String unitType,
        String resultType,
        String rankingStrategy,
        Integer participantLimit,
        BigDecimal entryFeeAmount,
        String entryFeeCurrency,
        String status,
        String settingsJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Integer distanceM,
        String stroke,
        String gender,
        java.util.List<com.acme.sportplatform.competition.AgeGroup> ageGroups,
        String seedingMode,
        UUID sessionId,
        Integer orderInSession,
        Integer heatDurationSec) {
        this(id, eventId, disciplineTemplateId, code, name, competitionFormat, unitType, resultType, rankingStrategy, participantLimit, entryFeeAmount, entryFeeCurrency, status, settingsJson, createdAt, updatedAt, distanceM, stroke, gender, ageGroups, seedingMode, sessionId, orderInSession, heatDurationSec, null, null);
    }
}
