package com.acme.sportplatform.organizations;

import java.util.UUID;

public interface OrganizationLookup {

    boolean existsById(UUID organizationId);
}