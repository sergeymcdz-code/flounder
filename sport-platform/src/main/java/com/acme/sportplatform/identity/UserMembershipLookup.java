package com.acme.sportplatform.identity;

import java.util.List;
import java.util.UUID;

// Implemented by the organization module to avoid a dependency cycle.
public interface UserMembershipLookup {
    List<Membership> membershipsOf(UUID userId);
    List<AuthenticationRow> authenticationOf(UUID userId);
    interface AuthenticationRow {
        String getEmail();
        String getStatus();
        String getRoleCode();
        UUID getOrganizationId();
        String getOrganizationName();
        String getMemberRole();
    }
    static String effectiveRole(String memberRole) {
        return switch (memberRole.toLowerCase(java.util.Locale.ROOT)) {
            case "owner", "admin", "organizer" -> "organizer";
            case "operator" -> "operator";
            case "judge" -> "judge";
            default -> null;
        };
    }
    record Membership(UUID organizationId, String organizationName, String memberRole) {}
}
