package com.acme.sportplatform.organizations;

import java.util.Set;
import java.util.UUID;

/** Current caller's organization scope for private and platform catalogs. */
public interface CatalogOrganizationAccess {
    boolean isPlatformAdmin();
    Set<UUID> readableOrganizationIds();
    UUID ownerForCreate(UUID requested);
    boolean canRead(UUID organizationId);
    void assertCanWrite(UUID organizationId);
    UUID userId();
}
