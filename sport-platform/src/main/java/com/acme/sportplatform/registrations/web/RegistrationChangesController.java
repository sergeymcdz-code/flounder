package com.acme.sportplatform.registrations.web;

import com.acme.sportplatform.registrations.api.*;
import com.acme.sportplatform.registrations.application.ChangeRegistrationUseCase;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationChangesController {
    private final ChangeRegistrationUseCase changes;

    public RegistrationChangesController(ChangeRegistrationUseCase changes) {
        this.changes = changes;
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public RegistrationResponse patch(
            @PathVariable UUID id,
            @RequestBody @Valid PatchRegistrationRequest r,
            @AuthenticationPrincipal
                    com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        return changes.patch(id, r, principal);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAnyRole('PARTICIPANT','PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public RegistrationResponse withdraw(
            @PathVariable UUID id,
            @AuthenticationPrincipal
                    com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        return changes.withdraw(id, principal);
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public RegistrationResponse resubmit(
            @PathVariable UUID id,
            @AuthenticationPrincipal(expression = "userId") UUID userId,
            @RequestBody(required = false) @Valid ResubmitRegistrationRequest request) {
        return changes.resubmit(id, userId, request);
    }
}
