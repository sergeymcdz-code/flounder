package com.acme.sportplatform.events.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventManagementAccess;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TemporaryEventManagementAccess implements EventManagementAccess {
    private final EventRepository events;

    public TemporaryEventManagementAccess(EventRepository events) {
        this.events = events;
    }

    public void assertCanManage(UUID eventId) {
        if (!canManage(eventId))
            throw new AccessDeniedException("Нет доступа к управлению событием");
    }

    public boolean canManage(UUID eventId) {
        var event =
                events.findById(eventId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "events.event_not_found", "Событие не найдено"));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        if (auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"))) return true;
        if (!(auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) return false;
        boolean role =
                auth.getAuthorities().stream()
                        .anyMatch(
                                a ->
                                        java.util.Set.of(
                                                        "ROLE_ORGANIZER",
                                                        "ROLE_OPERATOR",
                                                        "ROLE_JUDGE")
                                                .contains(a.getAuthority()));
        // TODO T01: delegate to EventAccessService.assertCanManage(eventId, principal).
        return role && principal.getUserId().equals(event.getCreatedByUserId());
    }
}
