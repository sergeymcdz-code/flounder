package com.acme.sportplatform.organizations;

import java.util.UUID;

public interface OrganizationManagementAccess {

    boolean isMember(UUID organizationId, UUID userId);

    boolean canManageOrganization(UUID organizationId, UUID userId);
}