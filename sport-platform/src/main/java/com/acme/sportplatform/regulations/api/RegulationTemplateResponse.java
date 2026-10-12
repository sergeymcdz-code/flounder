package com.acme.sportplatform.regulations.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RegulationTemplateResponse(
        UUID id,
        UUID sportId,
        String code,
        String name,
        String description,
        boolean isActive,
        OffsetDateTime createdAt
) {
}