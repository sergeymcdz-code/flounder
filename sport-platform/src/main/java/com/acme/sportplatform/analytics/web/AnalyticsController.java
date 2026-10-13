package com.acme.sportplatform.analytics.web;

import com.acme.sportplatform.analytics.api.*;
import com.acme.sportplatform.analytics.application.AnalyticsService;
import com.acme.sportplatform.analytics.application.AnalyticsExport;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ORGANIZER','PLATFORM_ADMIN')")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final AnalyticsExport export;
    public AnalyticsController(AnalyticsService analytics,AnalyticsExport export) { this.analytics=analytics;this.export=export; }
    @GetMapping("/events/{eventId}/analytics")
    public EventAnalytics event(@PathVariable UUID eventId,@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return analytics.event(eventId,principal);
    }
    @GetMapping("/organizations/{id}/analytics")
    public OrganizationAnalytics organization(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return analytics.organization(id,principal);
    }
    @GetMapping("/events/{eventId}/analytics/export.xlsx")
    public ResponseEntity<byte[]> exportEvent(@PathVariable UUID eventId,@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return download(export.event(analytics.event(eventId,principal)),"event-"+eventId+"-analytics.xlsx");
    }
    @GetMapping("/organizations/{id}/analytics/export.xlsx")
    public ResponseEntity<byte[]> exportOrganization(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return download(export.organization(analytics.organization(id,principal)),"organization-"+id+"-analytics.xlsx");
    }
    private ResponseEntity<byte[]> download(byte[] data,String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString())
                .cacheControl(CacheControl.noStore()).body(data);
    }
}
