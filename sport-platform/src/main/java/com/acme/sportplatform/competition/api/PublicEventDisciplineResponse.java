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
        String settingsJson
) {
}