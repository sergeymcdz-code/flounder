package com.acme.sportplatform.competition;

import java.math.BigDecimal;
import java.util.UUID;

public record EventDisciplineSummary(
        UUID id,
        UUID eventId,
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
        java.time.OffsetDateTime createdAt) {}
