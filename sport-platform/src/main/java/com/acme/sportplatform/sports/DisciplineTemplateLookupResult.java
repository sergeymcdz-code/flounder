package com.acme.sportplatform.sports;

import java.util.UUID;

public record DisciplineTemplateLookupResult(
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