package com.acme.sportplatform.events;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;

@Service
public class EventAccessService {
    private final EventRepository events;
    private final OrganizationManagementAccess organizations;

    public EventAccessService(EventRepository events, OrganizationManagementAccess organizations) {
        this.events = events;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public boolean canManage(UUID eventId, AuthenticatedUserPrincipal principal) {
        try {
            assertCanManage(eventId, principal);
            return true;
        } catch (BusinessException exception) {
            if ("events.access_denied".equals(exception.getCode())) return false;
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public void assertCanModify(UUID eventId, AuthenticatedUserPrincipal principal) {
        assertCanManage(eventId, principal);
        assertWritable(eventId);
    }

    @Transactional(readOnly = true)
    public void assertWritable(UUID eventId) {
        var event = events.findById(eventId).orElseThrow(() ->
                new BusinessException("events.event_not_found", "Событие не найдено"));
        if ("ARCHIVED".equals(event.getStatus()))
            throw new BusinessException("events.archived", "Архивное событие доступно только для чтения");
    }

    @Transactional(readOnly = true)
    public void assertCanManage(UUID eventId, AuthenticatedUserPrincipal principal) {
        assertAccess(eventId, principal, java.util.Set.of("owner", "admin", "organizer", "operator", "judge"));
    }

    @Transactional(readOnly = true)
    public void assertCanView(UUID eventId, AuthenticatedUserPrincipal principal) {
        assertAccess(eventId, principal, java.util.Set.of("owner", "admin", "organizer", "operator", "judge", "member"));
    }

    @Transactional(readOnly = true)
    public void assertCanAdminister(UUID eventId, AuthenticatedUserPrincipal principal) {
        assertAccess(eventId, principal, java.util.Set.of("owner", "admin", "organizer"));
    }

    private void assertAccess(UUID eventId, AuthenticatedUserPrincipal principal, java.util.Set<String> roles) {
        var event = events.findById(eventId)
                .orElseThrow(() -> new BusinessException("events.event_not_found", "Событие не найдено"));
        if (principal != null && principal.isPlatformAdmin()) return;
        if (principal != null && (principal.getUserId().equals(event.getCreatedByUserId())
                || principal.getMemberships().stream().anyMatch(m -> event.getOrganizationId().equals(m.organizationId())
                    && roles.contains(m.memberRole().toLowerCase(java.util.Locale.ROOT))))) return;
        throw new BusinessException("events.access_denied", "Нет доступа к этому событию");
    }
}
