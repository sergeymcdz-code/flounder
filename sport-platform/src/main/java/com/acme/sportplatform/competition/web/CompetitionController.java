package com.acme.sportplatform.competition.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.acme.sportplatform.competition.api.CreateEventDisciplineRequest;
import com.acme.sportplatform.competition.api.EventDisciplineResponse;
import com.acme.sportplatform.competition.application.CreateEventDisciplineUseCase;
import com.acme.sportplatform.competition.application.ListEventDisciplinesUseCase;
import com.acme.sportplatform.competition.application.PublishEventDisciplineUseCase;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/events/{eventId}/disciplines")
public class CompetitionController {
    private final CreateEventDisciplineUseCase createEventDisciplineUseCase;
    private final ListEventDisciplinesUseCase listEventDisciplinesUseCase;
    private final PublishEventDisciplineUseCase publishEventDisciplineUseCase;
    private final EventAccessService eventAccessService;

    public CompetitionController(CreateEventDisciplineUseCase createEventDisciplineUseCase,
            ListEventDisciplinesUseCase listEventDisciplinesUseCase,
            PublishEventDisciplineUseCase publishEventDisciplineUseCase,
            EventAccessService eventAccessService) {
        this.createEventDisciplineUseCase = createEventDisciplineUseCase;
        this.listEventDisciplinesUseCase = listEventDisciplinesUseCase;
        this.publishEventDisciplineUseCase = publishEventDisciplineUseCase;
        this.eventAccessService = eventAccessService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<EventDisciplineResponse> create(@PathVariable UUID eventId,
            @RequestBody @org.springframework.validation.annotation.Validated({jakarta.validation.groups.Default.class,CreateEventDisciplineRequest.Create.class}) CreateEventDisciplineRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanManage(eventId, principal);
        return ResponseEntity.ok(createEventDisciplineUseCase.execute(eventId, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR', 'JUDGE')")
    public ResponseEntity<List<EventDisciplineResponse>> list(@PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanManage(eventId, principal);
        return ResponseEntity.ok(listEventDisciplinesUseCase.execute(eventId));
    }

    @PostMapping("/{disciplineId}/publish")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<EventDisciplineResponse> publish(@PathVariable UUID eventId,
            @PathVariable UUID disciplineId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        eventAccessService.assertCanManage(eventId, principal);
        return ResponseEntity.ok(publishEventDisciplineUseCase.execute(eventId, disciplineId));
    }
}
