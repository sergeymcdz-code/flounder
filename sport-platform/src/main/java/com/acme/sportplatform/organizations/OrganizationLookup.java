package com.acme.sportplatform.organizations;

import java.util.UUID;

public interface OrganizationLookup {

    boolean existsById(UUID organizationId);

    String nameById(UUID organizationId);
}
