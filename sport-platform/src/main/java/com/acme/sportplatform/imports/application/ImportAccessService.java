package com.acme.sportplatform.imports.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ImportAccessService {
    private final EventLookup events;

    public ImportAccessService(EventLookup events) { this.events = events; }

    public void assertCanManage(UUID eventId, Authentication principal) {
        try { events.getById(eventId); }
        catch (IllegalArgumentException ex) {
            throw new BusinessException("imports.event_not_found", "Событие не найдено");
        }
        // TODO T01: delegate to EventAccessService.assertCanManage(eventId, principal).
        boolean admin = principal != null && principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        if (admin) return;
        if (principal != null && principal.getPrincipal() instanceof AuthenticatedUserPrincipal user
                && events.isCreatedBy(eventId, user.getUserId())) return;
        throw new BusinessException("imports.access_denied", "Нет доступа к управлению событием");
    }
}
