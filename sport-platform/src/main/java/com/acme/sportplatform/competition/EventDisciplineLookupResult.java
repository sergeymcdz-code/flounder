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
        String settingsJson
) {
}