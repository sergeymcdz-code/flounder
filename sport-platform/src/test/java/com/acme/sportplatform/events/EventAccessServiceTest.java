package com.acme.sportplatform.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

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
    void permitsAnyOrganizationMember() {
        existingEvent();
        UUID member = UUID.randomUUID();
        when(organizations.isMember(organizationId, member)).thenReturn(true);
        access.assertCanManage(eventId, () -> member);
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
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
        access.assertCanManage(eventId, null);
        verifyNoInteractions(organizations);
    }
}
