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
        String settingsJson
) {
}