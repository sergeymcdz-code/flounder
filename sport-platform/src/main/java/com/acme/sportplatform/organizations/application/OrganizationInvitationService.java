package com.acme.sportplatform.organizations.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.organizations.OrganizationInvitationCreatedEvent;
import com.acme.sportplatform.organizations.api.*;
import com.acme.sportplatform.organizations.domain.InvitationExpiredException;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationInvitationRepository;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationInvitationRepository.Invitation;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(noRollbackFor = InvitationExpiredException.class)
public class OrganizationInvitationService {
    private final OrganizationInvitationRepository invitations;
    private final OrganizationService organizations;
    private final UserLookup users;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();
    public OrganizationInvitationService(OrganizationInvitationRepository invitations, OrganizationService organizations,
            UserLookup users, ApplicationEventPublisher events) {
        this.invitations = invitations; this.organizations = organizations; this.users = users; this.events = events;
    }
    private void owner(UUID id, AuthenticatedUserPrincipal principal) {
        if (!principal.isPlatformAdmin() && !organizations.isOwnerOrAdmin(id, principal.getUserId()))
            throw new BusinessException("organizations.access_denied", "Только владелец или администратор организации может управлять приглашениями");
        organizations.nameById(id);
        invitations.lockOrganization(id);
    }
    public OrganizationInvitationResponse create(UUID organizationId, CreateInvitationRequest request, AuthenticatedUserPrincipal principal) {
        owner(organizationId, principal);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        invitations.expire(organizationId, now);
        if (invitations.pending(organizationId, email))
            throw new BusinessException("organizations.invitation_exists", "Приглашение на этот email уже отправлено");
        if (users.findByEmail(email).filter(u -> organizations.isMember(organizationId, u.id())).isPresent())
            throw new BusinessException("organizations.already_member", "Пользователь уже состоит в организации");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var invitation = invitations.create(organizationId, email, request.memberRole(), hash(token), principal.getUserId(), now);
        String organizationName = organizations.nameById(organizationId);
        String name = users.findById(principal.getUserId()).map(UserLookup.User::firstName)
                .filter(firstName -> !firstName.isBlank()).map(String::trim)
                .orElse("Организация " + organizationName);
        events.publishEvent(new OrganizationInvitationCreatedEvent(invitation.id(), email, name,
                organizationName, request.memberRole(), token));
        return invitation.response();
    }
    public List<OrganizationInvitationResponse> list(UUID organizationId, String status, AuthenticatedUserPrincipal principal) {
        owner(organizationId, principal);
        if (status != null && !Set.of("PENDING", "ACCEPTED", "REVOKED", "EXPIRED").contains(status))
            throw new BusinessException("organizations.invalid_invitation_status", "Неизвестный статус приглашения");
        invitations.expire(organizationId, OffsetDateTime.now(ZoneOffset.UTC));
        return invitations.list(organizationId, status);
    }
    public void revoke(UUID organizationId, UUID id, AuthenticatedUserPrincipal principal) {
        owner(organizationId, principal);
        invitations.expire(organizationId, OffsetDateTime.now(ZoneOffset.UTC));
        var invitation = invitations.byId(organizationId, id).orElseThrow(this::notFound);
        requirePending(invitation);
        invitations.revoke(id);
    }
    public InvitationResponse get(String token) {
        var invitation = resolve(token);
        requirePending(invitation);
        return new InvitationResponse(organizations.nameById(invitation.organizationId()), invitation.memberRole(),
                invitation.email(), invitation.expiresAt(), invitation.status(), users.findByEmail(invitation.email()).isPresent());
    }
    public OrganizationMemberResponse accept(String token, AuthenticatedUserPrincipal principal) {
        var invitation = resolve(token);
        requirePending(invitation);
        var user = users.findById(principal.getUserId()).orElseThrow(() ->
                new BusinessException("organizations.user_not_found", "Пользователь не найден"));
        if (!user.email().equalsIgnoreCase(invitation.email()))
            throw new BusinessException("organizations.invitation_email_mismatch", "Войдите под email, на который отправлено приглашение");
        if (organizations.isMember(invitation.organizationId(), user.id()))
            throw new BusinessException("organizations.already_member", "Пользователь уже состоит в организации");
        var member = organizations.putMember(invitation.organizationId(), user.id(), invitation.memberRole());
        invitations.accept(invitation.id(), user.id(), OffsetDateTime.now(ZoneOffset.UTC));
        return new OrganizationMemberResponse(member.getId(), member.getOrganizationId(), member.getUserId(),
                member.getMemberRole(), member.getCreatedAt(), user.email(), user.firstName(), user.lastName());
    }
    private Invitation resolve(String token) {
        var first = invitations.byHash(hash(token)).orElseThrow(this::notFound);
        invitations.lockOrganization(first.organizationId());
        invitations.expire(first.organizationId(), OffsetDateTime.now(ZoneOffset.UTC));
        return invitations.byId(first.organizationId(), first.id()).orElseThrow(this::notFound);
    }
    private void requirePending(Invitation invitation) {
        if ("EXPIRED".equals(invitation.status())) throw new InvitationExpiredException();
        if (!"PENDING".equals(invitation.status()))
            throw new BusinessException("organizations.invitation_unavailable", "Приглашение уже принято или отозвано");
    }
    private BusinessException notFound() { return new BusinessException("organizations.invitation_not_found", "Приглашение не найдено"); }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
