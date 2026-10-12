package com.acme.sportplatform.organizations.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.acme.sportplatform.common.exception.BusinessException;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.api.CreateOrganizationRequest;
import com.acme.sportplatform.organizations.api.OrganizationResponse;
import com.acme.sportplatform.organizations.application.OrganizationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private static final String PLATFORM_ADMIN_ROLE =
            "ROLE_PLATFORM_ADMIN";

    private final OrganizationService organizationService;

    public OrganizationController(
            OrganizationService organizationService
    ) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<OrganizationResponse> createOrganization(
            @RequestBody @Valid CreateOrganizationRequest request
    ) {
        OrganizationResponse response =
                organizationService.createOrganization(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')"
    )
    public ResponseEntity<OrganizationResponse> getOrganization(
            @PathVariable UUID id,
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        boolean platformAdmin =
                isPlatformAdmin(authentication);

        UUID currentUserId =
                getCurrentUserId(principal, platformAdmin);

        return ResponseEntity.ok(
                organizationService.getOrganization(
                        id,
                        currentUserId,
                        platformAdmin
                )
        );
    }

    @GetMapping
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')"
    )
    public ResponseEntity<List<OrganizationResponse>>
            getOrganizations(
                    @AuthenticationPrincipal
                    AuthenticatedUserPrincipal principal,
                    Authentication authentication
            ) {
        boolean platformAdmin =
                isPlatformAdmin(authentication);

        UUID currentUserId =
                getCurrentUserId(principal, platformAdmin);

        return ResponseEntity.ok(
                organizationService.getOrganizations(
                        currentUserId,
                        platformAdmin
                )
        );
    }

    private UUID getCurrentUserId(
            AuthenticatedUserPrincipal principal,
            boolean platformAdmin
    ) {
        if (platformAdmin) {
            return null;
        }

        if (principal == null) {
            throw new BusinessException(
                    "identity.invalid_principal",
                    "Authenticated user principal is unavailable"
            );
        }

        return principal.getUserId();
    }

    private boolean isPlatformAdmin(
            Authentication authentication
    ) {
        return authentication != null
                && authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                PLATFORM_ADMIN_ROLE.equals(
                                        authority.getAuthority()
                                )
                        );
    }
}