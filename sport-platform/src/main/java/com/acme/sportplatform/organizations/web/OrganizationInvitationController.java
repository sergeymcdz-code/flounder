package com.acme.sportplatform.organizations.web;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.api.*;
import com.acme.sportplatform.organizations.application.OrganizationInvitationService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrganizationInvitationController {
    private final OrganizationInvitationService invitations;
    public OrganizationInvitationController(OrganizationInvitationService invitations) { this.invitations = invitations; }
    @PostMapping("/organizations/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public OrganizationInvitationResponse create(@PathVariable UUID id, @Valid @RequestBody CreateInvitationRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) { return invitations.create(id, request, principal); }
    @GetMapping("/organizations/{id}/invitations")
    @PreAuthorize("isAuthenticated()")
    public List<OrganizationInvitationResponse> list(@PathVariable UUID id, @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) { return invitations.list(id, status, principal); }
    @DeleteMapping("/organizations/{id}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void revoke(@PathVariable UUID id, @PathVariable UUID invitationId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) { invitations.revoke(id, invitationId, principal); }
    @GetMapping("/invitations/{token}")
    public InvitationResponse get(@PathVariable String token) { return invitations.get(token); }
    @PostMapping("/invitations/{token}/accept")
    @PreAuthorize("isAuthenticated()")
    public OrganizationMemberResponse accept(@PathVariable String token,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) { return invitations.accept(token, principal); }
}
