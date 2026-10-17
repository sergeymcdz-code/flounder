package com.acme.sportplatform.organizations;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface OrganizationLookup {

    boolean existsById(UUID organizationId);

    String nameById(UUID organizationId);

    PublicOrganization publicBySlug(String slug);

    Map<UUID, PublicOrganizationCard> cards(Collection<UUID> ids);
}
