package com.acme.sportplatform.sports.api;

import java.util.UUID;

public record DisciplineTemplateResponse(
        UUID id,
        UUID sportId,
        String code,
        String name,
        String competitionFormat,
        String unitType,
        String resultType,
        String rankingStrategy,
        String defaultMeta
) {
}