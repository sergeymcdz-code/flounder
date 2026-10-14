package com.acme.sportplatform.organizations;

import java.util.Set;
import java.util.UUID;

public interface OrganizationManagementAccess {

    Set<UUID> organizationIdsOf(UUID userId);

    boolean isMember(UUID organizationId, UUID userId);

    boolean canManageOrganization(UUID organizationId, UUID userId);

    boolean isOwnerOrAdmin(UUID organizationId, UUID userId);
}
