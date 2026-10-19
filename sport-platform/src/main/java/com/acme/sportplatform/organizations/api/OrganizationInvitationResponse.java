package com.acme.sportplatform.organizations.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationInvitationResponse(UUID id, UUID organizationId, String email, String memberRole,
        String status, OffsetDateTime expiresAt, OffsetDateTime createdAt) {}
