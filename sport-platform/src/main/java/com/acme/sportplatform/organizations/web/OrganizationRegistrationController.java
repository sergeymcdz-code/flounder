package com.acme.sportplatform.organizations.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.api.CreateOrganizationRegistrationRequest;
import com.acme.sportplatform.organizations.api.OrganizationRegistrationResponse;
import com.acme.sportplatform.organizations.application.OrganizationRegistrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organization-registration-requests")
public class OrganizationRegistrationController {

    private final OrganizationRegistrationService registrationService;

    public OrganizationRegistrationController(
            OrganizationRegistrationService registrationService
    ) {
        this.registrationService = registrationService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationRegistrationResponse> create(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @RequestBody @Valid
            CreateOrganizationRegistrationRequest request
    ) {
        OrganizationRegistrationResponse response =
                registrationService.create(
                        principal.getUserId(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/me/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationRegistrationResponse> getMyPending(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(
                registrationService.getPendingForUser(
                        principal.getUserId()
                )
        );
    }
}