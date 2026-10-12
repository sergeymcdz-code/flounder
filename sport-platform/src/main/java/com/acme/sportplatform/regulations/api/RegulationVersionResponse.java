package com.acme.sportplatform.regulations.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RegulationVersionResponse(
        UUID id,
        UUID regulationTemplateId,
        int versionNo,
        String status,
        LocalDate effectiveFrom,
        RegulationRules rulesJson,
        String notes,
        UUID createdBy,
        OffsetDateTime createdAt
) {
}