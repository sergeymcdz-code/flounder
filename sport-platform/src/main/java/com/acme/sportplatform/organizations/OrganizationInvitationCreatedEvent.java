package com.acme.sportplatform.organizations;

import java.util.UUID;

public record OrganizationInvitationCreatedEvent(UUID invitationId, String email, String inviterName,
        String organizationName, String memberRole, String token) {}
