package com.acme.sportplatform.organizations.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationMemberResponse(UUID id, UUID organizationId, UUID userId, String memberRole,
                                         OffsetDateTime createdAt, String email, String firstName, String lastName) {}
