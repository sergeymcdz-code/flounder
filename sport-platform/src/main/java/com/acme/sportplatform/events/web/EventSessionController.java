package com.acme.sportplatform.events.web;

import com.acme.sportplatform.events.*;
import com.acme.sportplatform.events.api.EventSessionRequest;
import com.acme.sportplatform.events.application.EventSessionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
public class EventSessionController {
    private final EventSessionService sessions;

    public EventSessionController(EventSessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/events/{eventId}/sessions")
    public List<EventSessionView> list(@PathVariable UUID eventId) {
        return sessions.list(eventId);
    }

    @PostMapping("/events/{eventId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public EventSessionView create(
            @PathVariable UUID eventId, @RequestBody @Valid EventSessionRequest r) {
        return sessions.create(eventId, r);
    }

    @PatchMapping("/event-sessions/{id}")
    public EventSessionView patch(
            @PathVariable UUID id, @RequestBody @Valid EventSessionRequest r) {
        return sessions.patch(id, r);
    }

    @DeleteMapping("/event-sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        sessions.delete(id);
    }
}
