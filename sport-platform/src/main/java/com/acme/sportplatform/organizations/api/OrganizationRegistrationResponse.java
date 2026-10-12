package com.acme.sportplatform.organizations.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationRegistrationResponse(
        UUID id,
        UUID applicantUserId,
        String organizationType,
        String organizationName,
        String legalName,
        String inn,
        String contactEmail,
        String contactPhone,
        String status,
        String rejectionReason,
        UUID reviewedByUserId,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}