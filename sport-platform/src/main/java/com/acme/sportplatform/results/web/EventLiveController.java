package com.acme.sportplatform.results.web;

import com.acme.sportplatform.results.api.*;
import com.acme.sportplatform.results.application.EventLiveUseCase;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR','JUDGE')")
public class EventLiveController {
    private final EventLiveUseCase live;

    public EventLiveController(EventLiveUseCase live) {
        this.live = live;
    }

    @PutMapping("/events/{eventId}/live")
    public LiveStateResponse set(
            @PathVariable UUID eventId,
            @RequestBody SetLiveStateRequest r,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        return live.set(eventId, r.currentUnitId(), principal);
    }

    @PostMapping("/competition-units/{id}/start")
    public LiveStateResponse start(
            @PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        return live.start(id, principal);
    }

    @PostMapping("/competition-units/{id}/finish")
    public LiveStateResponse finish(
            @PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        return live.finish(id, principal);
    }
}
