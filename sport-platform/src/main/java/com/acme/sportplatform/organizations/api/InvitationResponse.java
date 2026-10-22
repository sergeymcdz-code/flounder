package com.acme.sportplatform.organizations.api;

import java.time.OffsetDateTime;

public record InvitationResponse(String organizationName, String memberRole, String email,
        OffsetDateTime expiresAt, String status, boolean userExists) {}
