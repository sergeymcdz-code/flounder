package com.acme.sportplatform.organizations.application;

import java.util.Optional;
import java.util.UUID;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.identity.UserRoleLookup;
import com.acme.sportplatform.organizations.api.AddOrganizationMemberRequest;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberRepository;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrganizationMemberRolesTest {
    final UUID org = UUID.randomUUID();
    final UUID actor = UUID.randomUUID();
    final UUID target = UUID.randomUUID();
    final OrganizationRepository organizations = mock(OrganizationRepository.class);
    final OrganizationMemberRepository members = mock(OrganizationMemberRepository.class);
    final UserLookup users = mock(UserLookup.class);
    final OrganizationService service = new OrganizationService(organizations, members, mock(UserRoleLookup.class),
            users, new ObjectMapper(), mock(PublicOrganizationProfileService.class));

    @BeforeEach
    void setUp() {
        when(organizations.lockById(org)).thenReturn(Optional.of(new OrganizationEntity()));
        when(organizations.existsById(org)).thenReturn(true);
        when(members.save(any())).thenAnswer(call -> call.getArgument(0));
        when(users.findById(any())).thenAnswer(call -> Optional.of(new UserLookup.User(
                call.getArgument(0), "test@example.org", null, null)));
    }

    OrganizationMemberEntity membership(UUID user, String role) {
        var member = new OrganizationMemberEntity();
        member.setOrganizationId(org); member.setUserId(user); member.setMemberRole(role);
        when(members.findByOrganizationIdAndUserId(org, user)).thenReturn(Optional.of(member));
        return member;
    }

    @ParameterizedTest
    @CsvSource({"admin,false", "organizer,false", "manager,false", "missing,true", "admin,true"})
    void nonOwnersCannotAssignOrRemoveOwnersThroughAnyMutation(String role, boolean platformAdmin) {
        if (!role.equals("missing")) membership(actor, role);
        membership(target, "member");
        denied(() -> service.addMember(org, new AddOrganizationMemberRequest(target, "OWNER"), actor, platformAdmin));
        denied(() -> service.updateMember(org, target, "owner", actor, platformAdmin));
        membership(target, "owner");
        denied(() -> service.addMember(org, new AddOrganizationMemberRequest(target, "member"), actor, platformAdmin));
        denied(() -> service.updateMember(org, target, "member", actor, platformAdmin));
        denied(() -> service.deleteMember(org, target, actor, platformAdmin));
        verify(members, never()).save(any());
        verify(members, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "patch", "delete", "internal"})
    void lastOwnerCannotBeRemovedOrDemoted(String operation) {
        membership(actor, "OWNER");
        when(members.countByOrganizationIdAndMemberRoleIgnoreCase(org, "owner")).thenReturn(1L);
        assertThatThrownBy(() -> changeSelf(operation)).isInstanceOf(LastOrganizationOwnerException.class)
                .hasMessage("Нельзя удалить или понизить последнего владельца организации");
        verify(members, never()).save(any());
        verify(members, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "patch", "delete"})
    void ownerCanLeaveWhenAnotherOwnerRemains(String operation) {
        membership(actor, "owner");
        when(members.countByOrganizationIdAndMemberRoleIgnoreCase(org, "owner")).thenReturn(2L);
        changeSelf(operation);
        if (operation.equals("delete")) verify(members).delete(any());
        else verify(members).save(any());
    }

    @Test
    void ownerCanPromoteAndDemoteAnotherOwner() {
        membership(actor, "owner");
        var member = membership(target, "member");
        assertThat(service.addMember(org, new AddOrganizationMemberRequest(target, "OWNER"), actor, false).memberRole())
                .isEqualTo("owner");
        when(members.countByOrganizationIdAndMemberRoleIgnoreCase(org, "owner")).thenReturn(2L);
        assertThat(service.updateMember(org, target, "admin", actor, false).memberRole()).isEqualTo("admin");
        assertThat(member.getMemberRole()).isEqualTo("admin");
    }

    @Test
    void membershipLockPrecedesAuthorizationAndMutation() {
        membership(actor, "owner");
        membership(target, "member");
        service.updateMember(org, target, "operator", actor, false);
        var order = inOrder(organizations, members);
        order.verify(organizations).lockById(org);
        order.verify(organizations).existsById(org);
        order.verify(members).findByOrganizationIdAndUserId(org, actor);
    }

    void changeSelf(String operation) {
        switch (operation) {
            case "post" -> service.addMember(org, new AddOrganizationMemberRequest(actor, "member"), actor, false);
            case "patch" -> service.updateMember(org, actor, "admin", actor, false);
            case "delete" -> service.deleteMember(org, actor, actor, false);
            case "internal" -> service.putMember(org, actor, "member", actor);
            default -> throw new AssertionError(operation);
        }
    }

    void denied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isIn("organizations.access_denied", "organizations.protected_member"));
    }
}
