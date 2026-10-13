package com.acme.sportplatform.organizations.application;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.acme.sportplatform.identity.UserRoleLookup;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberRepository;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationServiceTest {
    @ParameterizedTest
    @CsvSource({"owner,true", "ADMIN,true", "organizer,true", "operator,false", "judge,false", "member,false"})
    void eventCreationRequiresManagementMembership(String role, boolean allowed) {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var memberships = mock(OrganizationMemberRepository.class);
        var member = new OrganizationMemberEntity();
        member.setMemberRole(role);
        when(memberships.findByOrganizationIdAndUserId(organizationId, userId)).thenReturn(Optional.of(member));
        var service = new OrganizationService(mock(OrganizationRepository.class), memberships, mock(UserRoleLookup.class), mock(com.acme.sportplatform.identity.UserLookup.class), new com.fasterxml.jackson.databind.ObjectMapper());
        assertEquals(allowed, service.canManageOrganization(organizationId, userId));
    }
}
