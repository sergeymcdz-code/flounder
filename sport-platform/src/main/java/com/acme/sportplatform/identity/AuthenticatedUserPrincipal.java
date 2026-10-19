package com.acme.sportplatform.identity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;

public interface AuthenticatedUserPrincipal {

    UUID getUserId();

    default Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    default List<UserMembershipLookup.Membership> getMemberships() {
        return List.of();
    }

    default boolean isPlatformAdmin() {
        return getAuthorities().stream().anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
    }
}