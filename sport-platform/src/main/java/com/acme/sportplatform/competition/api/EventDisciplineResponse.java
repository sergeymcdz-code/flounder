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
        OffsetDateTime updatedAt
) {
}