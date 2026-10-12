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
    public void assertCanManage(UUID eventId, AuthenticatedUserPrincipal principal) {
        var event = events.findById(eventId)
                .orElseThrow(() -> new BusinessException("events.event_not_found", "Событие не найдено"));
        if (principal != null && principal.isPlatformAdmin()) return;
        if (principal != null && (principal.getUserId().equals(event.getCreatedByUserId())
                || organizations.isMember(event.getOrganizationId(), principal.getUserId()))) return;
        throw new BusinessException("events.access_denied", "Нет доступа к управлению этим событием");
    }
}
