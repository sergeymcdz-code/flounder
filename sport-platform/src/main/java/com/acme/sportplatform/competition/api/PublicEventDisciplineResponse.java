package com.acme.sportplatform.competition.api;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicEventDisciplineResponse(
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
        String settingsJson,
        Integer distanceM,
        String stroke,
        String gender,
        java.util.List<com.acme.sportplatform.competition.AgeGroup> ageGroups,
        String seedingMode,
        UUID sessionId,
        Integer orderInSession,
        Integer heatDurationSec) {}
