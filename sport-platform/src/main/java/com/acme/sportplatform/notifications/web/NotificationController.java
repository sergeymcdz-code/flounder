package com.acme.sportplatform.notifications.web;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.notifications.api.*;
import com.acme.sportplatform.notifications.application.NotificationManagementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationManagementService service;
    public NotificationController(NotificationManagementService service) { this.service = service; }
    @GetMapping("/organizations/{id}/notification-settings")
    @PreAuthorize("hasAnyRole('ORGANIZER','PLATFORM_ADMIN')")
    public List<NotificationSetting> settings(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.settings(id, principal);
    }
    @PutMapping("/organizations/{id}/notification-settings")
    @PreAuthorize("hasAnyRole('ORGANIZER','PLATFORM_ADMIN')")
    public List<NotificationSetting> update(@PathVariable UUID id, @RequestBody @Valid List<@Valid NotificationSetting> settings,
                                           @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.update(id, settings, principal);
    }
    @GetMapping("/events/{eventId}/notifications")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public NotificationLogPage journal(@PathVariable UUID eventId, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.journal(eventId, status, page, principal);
    }
}
