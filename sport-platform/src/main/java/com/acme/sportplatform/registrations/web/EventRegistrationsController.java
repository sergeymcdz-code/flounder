package com.acme.sportplatform.registrations.web;

import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.application.ListRegistrationsForReviewUseCase;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;

@RestController
@RequestMapping("/api/v1/events/{eventId}/registrations")
public class EventRegistrationsController {
    private final EventAccessService access;
    private final ListRegistrationsForReviewUseCase list;
    public EventRegistrationsController(EventAccessService access, ListRegistrationsForReviewUseCase list) {
        this.access = access; this.list = list;
    }
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    public List<RegistrationResponse> list(@PathVariable UUID eventId, @RequestParam(required = false) RegistrationStatus status,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal); return list.execute(eventId, status, principal);
    }
}
