package com.acme.sportplatform.organizations;

import java.util.UUID;

public interface OrganizationManagementAccess {

    boolean canManageOrganization(UUID organizationId, UUID userId);
}