package com.acme.sportplatform.identity;

import java.util.List;
import java.util.UUID;

// Implemented by the organization module to avoid a dependency cycle.
public interface UserMembershipLookup {
    List<Membership> membershipsOf(UUID userId);
    record Membership(UUID organizationId, String organizationName, String memberRole) {}
}
