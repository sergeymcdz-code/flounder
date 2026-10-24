package com.acme.sportplatform.organizations.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.CatalogOrganizationAccess;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CatalogOrganizationAccessService implements CatalogOrganizationAccess {
    private final OrganizationManagementAccess memberships;
    public CatalogOrganizationAccessService(OrganizationManagementAccess memberships) { this.memberships = memberships; }

    private Authentication caller() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            throw new BusinessException("organizations.access_denied", "Доступ запрещён");
        return auth;
    }
    @Override public boolean isPlatformAdmin() {
        return caller().getAuthorities().stream().anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
    }
    @Override public UUID userId() {
        return caller().getPrincipal() instanceof AuthenticatedUserPrincipal principal ? principal.getUserId() : null;
    }
    @Override public Set<UUID> readableOrganizationIds() {
        var id = userId();
        return id == null ? Set.of() : memberships.organizationIdsOf(id);
    }
    @Override public boolean canRead(UUID organizationId) {
        return isPlatformAdmin() || organizationId == null || readableOrganizationIds().contains(organizationId);
    }
    @Override public void assertCanWrite(UUID organizationId) {
        if (isPlatformAdmin()) return;
        var id = userId();
        if (organizationId == null || id == null || !memberships.canManageOrganization(organizationId, id))
            throw new BusinessException("organizations.access_denied", "Доступ запрещён");
    }
    @Override public UUID ownerForCreate(UUID requested) {
        if (isPlatformAdmin()) return requested;
        if (requested == null) {
            var id = userId();
            var organizations = id == null ? Set.<UUID>of() : memberships.manageableOrganizationIdsOf(id);
            if (organizations.size() != 1)
                throw new BusinessException("organizations.organization_required", "Укажите организацию для записи каталога");
            requested = organizations.iterator().next();
        }
        assertCanWrite(requested);
        return requested;
    }
}
