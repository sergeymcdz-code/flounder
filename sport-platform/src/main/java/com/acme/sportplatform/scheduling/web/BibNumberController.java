package com.acme.sportplatform.scheduling.web;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.scheduling.application.BibNumberService;
import com.acme.sportplatform.scheduling.domain.BibAllocator.Mode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/bib-numbers")
@PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
public class BibNumberController {
    public record AssignRequest(
            @NotNull Mode mode, @Positive Integer startFrom, Boolean reassign) {}

    public record ChangeRequest(@NotNull @Positive Integer bibNumber) {}

    private final BibNumberService service;

    public BibNumberController(BibNumberService service) {
        this.service = service;
    }

    @PostMapping("/assign")
    public BibNumberService.Assignment assign(
            @PathVariable UUID eventId,
            @RequestBody @Valid AssignRequest body,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.assign(
                eventId,
                body.mode(),
                body.startFrom() == null ? 1 : body.startFrom(),
                Boolean.TRUE.equals(body.reassign()),
                principal);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('JUDGE','ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public List<BibNumberService.Row> list(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.list(eventId, principal);
    }

    @PatchMapping("/{profileId}")
    public BibNumberService.Row change(
            @PathVariable UUID eventId,
            @PathVariable UUID profileId,
            @RequestBody @Valid ChangeRequest body,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.change(eventId, profileId, body.bibNumber(), principal);
    }
}
