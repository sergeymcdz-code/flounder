package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

final class AdminSelfProtection {
    private AdminSelfProtection() {}
    static void assertNotSelf(UUID userId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
                && userId.equals(principal.getUserId())) {
            throw new BusinessException("identity.cannot_modify_self", "Нельзя заблокировать себя или снять у себя роль администратора платформы");
        }
    }
}
