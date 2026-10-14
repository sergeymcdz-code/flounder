package com.acme.sportplatform.competition;

import java.math.BigDecimal;
import java.util.UUID;

public record EventDisciplineLookupResult(
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
        String settingsJson,
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
    public EventDisciplineLookupResult(
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
        String settingsJson,
        Integer distanceM,
        String stroke,
        String gender,
        java.util.List<com.acme.sportplatform.competition.AgeGroup> ageGroups,
        String seedingMode,
        UUID sessionId,
        Integer orderInSession,
        Integer heatDurationSec) {
        this(id, eventId, disciplineTemplateId, code, name, competitionFormat, unitType, resultType, rankingStrategy, participantLimit, entryFeeAmount, entryFeeCurrency, settingsJson, distanceM, stroke, gender, ageGroups, seedingMode, sessionId, orderInSession, heatDurationSec, null, null);
    }
}
