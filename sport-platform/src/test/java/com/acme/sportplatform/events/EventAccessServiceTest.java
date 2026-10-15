package com.acme.sportplatform.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventAccessServiceTest {
    private final EventRepository events = mock(EventRepository.class);
    private final OrganizationManagementAccess organizations = mock(OrganizationManagementAccess.class);
    private final EventAccessService access = new EventAccessService(events, organizations);
    private final UUID eventId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID creator = UUID.randomUUID();

    private void existingEvent() {
        var event = new EventEntity();
        event.setCreatedByUserId(creator);
        event.setOrganizationId(organizationId);
        when(events.findById(eventId)).thenReturn(Optional.of(event));
    }

    @Test
    void permitsCreatorWithoutMembership() {
        existingEvent();
        access.assertCanManage(eventId, () -> creator);
        verifyNoInteractions(organizations);
    }

    @Test
    void permitsOperatorMembership() {
        existingEvent();
        UUID member = UUID.randomUUID();
        var principal = new PlatformUserPrincipal(member, "member@example.com", "", "active", List.of(),
                List.of(new com.acme.sportplatform.identity.UserMembershipLookup.Membership(organizationId, "Клуб", "operator")));
        access.assertCanManage(eventId, principal);
        assertThrows(BusinessException.class, () -> access.assertCanAdminister(eventId, principal));
    }

    @Test
    void deniesOutsiderWithBusinessError() {
        existingEvent();
        UUID outsider = UUID.randomUUID();
        var error = assertThrows(BusinessException.class, () -> access.assertCanManage(eventId, () -> outsider));
        assertEquals("events.access_denied", error.getCode());
    }

    @Test
    void permitsPlatformAdmin() {
        existingEvent();
        var principal = new PlatformUserPrincipal(UUID.randomUUID(), "admin@example.com", "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        access.assertCanManage(eventId, principal);
        verifyNoInteractions(organizations);
    }
    @Test
    void memberCanViewButCannotManageEvenWithGlobalOperatorRole() {
        existingEvent();
        var principal = new PlatformUserPrincipal(UUID.randomUUID(), "member@example.com", "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")),
                List.of(new com.acme.sportplatform.identity.UserMembershipLookup.Membership(organizationId, "Клуб", "member")));
        access.assertCanView(eventId, principal);
        assertThrows(BusinessException.class, () -> access.assertCanManage(eventId, principal));
        assertThrows(BusinessException.class, () -> access.assertCanAdminister(eventId, principal));
    }
}
