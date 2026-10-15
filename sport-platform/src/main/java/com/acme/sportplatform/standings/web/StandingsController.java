package com.acme.sportplatform.standings.web;

import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.standings.StandingsView;
import com.acme.sportplatform.standings.api.StandingsConfigRequest;
import com.acme.sportplatform.standings.domain.StandingsConfig;
import com.acme.sportplatform.standings.application.StandingsService;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StandingsController {
    private final EventAccessService access;
    private final StandingsService standings;
    public StandingsController(EventAccessService access, StandingsService standings) { this.access = access; this.standings = standings; }

    @GetMapping("/events/{eventId}/standings/config")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public StandingsConfig config(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return standings.config(eventId);
    }
    @PutMapping("/events/{eventId}/standings/config")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public StandingsConfig save(@PathVariable UUID eventId, @RequestBody StandingsConfigRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanModify(eventId, principal);
        return standings.save(eventId, request);
    }
    @GetMapping("/events/{eventId}/standings")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public StandingsView standings(@PathVariable UUID eventId, @RequestParam(defaultValue = "false") boolean details,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return standings.forEvent(eventId, details);
    }
    @GetMapping("/public/events/{slug}/standings")
    public ResponseEntity<StandingsView> published(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(15, TimeUnit.SECONDS).cachePublic())
                .body(standings.publicBySlug(slug));
    }
}
