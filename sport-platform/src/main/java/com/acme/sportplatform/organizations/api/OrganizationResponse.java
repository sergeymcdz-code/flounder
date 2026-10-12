package com.acme.sportplatform.organizations.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String type,
        String name,
        String legalName,
        String inn,
        String contactEmail,
        String contactPhone,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}