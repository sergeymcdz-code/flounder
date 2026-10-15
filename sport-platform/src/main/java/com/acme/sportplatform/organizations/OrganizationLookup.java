package com.acme.sportplatform.organizations;

import java.util.UUID;

public interface OrganizationLookup {

    boolean existsById(UUID organizationId);

    String nameById(UUID organizationId);

    PublicOrganization publicBySlug(String slug);

    java.util.Map<UUID, PublicOrganizationCard> cards(java.util.Collection<UUID> ids);
}
