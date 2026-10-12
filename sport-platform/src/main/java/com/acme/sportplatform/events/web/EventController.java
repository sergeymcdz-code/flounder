package com.acme.sportplatform.events.web;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.events.api.CreateEventRequest;
import com.acme.sportplatform.events.api.EventResponse;
import com.acme.sportplatform.events.application.CloseEventRegistrationUseCase;
import com.acme.sportplatform.events.application.CompleteEventUseCase;
import com.acme.sportplatform.events.application.CreateEventUseCase;
import com.acme.sportplatform.events.application.EventMapper;
import com.acme.sportplatform.events.application.OpenEventRegistrationUseCase;
import com.acme.sportplatform.events.application.PublishEventUseCase;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EventController {

    private static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";

    private final OrganizationManagementAccess organizationManagementAccess;
    private final CreateEventUseCase createEventUseCase;
    private final PublishEventUseCase publishEventUseCase;
    private final OpenEventRegistrationUseCase openEventRegistrationUseCase;
    private final CloseEventRegistrationUseCase closeEventRegistrationUseCase;
    private final CompleteEventUseCase completeEventUseCase;
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final EventAccessService eventAccessService;

    public EventController(
            CreateEventUseCase createEventUseCase,
            PublishEventUseCase publishEventUseCase,
            OpenEventRegistrationUseCase openEventRegistrationUseCase,
            CloseEventRegistrationUseCase closeEventRegistrationUseCase,
            CompleteEventUseCase completeEventUseCase,
            EventRepository eventRepository,
            EventMapper eventMapper,
            OrganizationManagementAccess organizationManagementAccess,
            EventAccessService eventAccessService
    ) {
        this.createEventUseCase = createEventUseCase;
        this.publishEventUseCase = publishEventUseCase;
        this.openEventRegistrationUseCase = openEventRegistrationUseCase;
        this.closeEventRegistrationUseCase = closeEventRegistrationUseCase;
        this.completeEventUseCase = completeEventUseCase;
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
        this.organizationManagementAccess = organizationManagementAccess;
        this.eventAccessService = eventAccessService;
    }

    @PostMapping("/events")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public ResponseEntity<EventResponse> create(
            @RequestBody @Valid CreateEventRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        UUID currentUserId = requireUserId(principal);

        requireOrganizationAccess(
                request.organizationId(),
                principal,
                authentication
        );

        EventResponse response = createEventUseCase.execute(
                request,
                currentUserId
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/events/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<EventResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        EventEntity entity = getManagedEvent(
                id,
                principal
        );

        return ResponseEntity.ok(eventMapper.toResponse(entity));
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<EventResponse>> list() {
        List<EventResponse> events = eventRepository.findAll().stream()
                .map(eventMapper::toResponse)
                .toList();

        return ResponseEntity.ok(events);
    }

    @GetMapping("/organizer/events")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<List<EventResponse>> listMyEvents(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        UUID currentUserId = requireUserId(principal);

        List<EventResponse> events = eventRepository.findAll().stream()
                .filter(event -> EventAccessService.isPlatformAdmin()
                        || currentUserId.equals(event.getCreatedByUserId())
                        || organizationManagementAccess.isMember(event.getOrganizationId(), currentUserId))
                .sorted(java.util.Comparator.comparing(EventEntity::getEventStartAt).reversed())
                .map(eventMapper::toResponse)
                .toList();

        return ResponseEntity.ok(events);
    }

    @PostMapping("/events/{id}/publish")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<EventResponse> publish(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        getManagedEvent(id, principal);
        return ResponseEntity.ok(publishEventUseCase.execute(id));
    }

    @PostMapping("/events/{id}/open-registration")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<EventResponse> openRegistration(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        getManagedEvent(id, principal);
        return ResponseEntity.ok(openEventRegistrationUseCase.execute(id));
    }

    @PostMapping("/events/{id}/close-registration")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<EventResponse> closeRegistration(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        getManagedEvent(id, principal);
        return ResponseEntity.ok(closeEventRegistrationUseCase.execute(id));
    }

    @PostMapping("/events/{id}/complete")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')")
    public ResponseEntity<EventResponse> complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        getManagedEvent(id, principal);
        return ResponseEntity.ok(completeEventUseCase.execute(id));
    }

    private EventEntity getManagedEvent(
            UUID eventId,
            AuthenticatedUserPrincipal principal
    ) {
        EventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "events.event_not_found",
                        "Событие не найдено"
                ));

        eventAccessService.assertCanManage(eventId, principal);

        return event;
    }

    private void requireOrganizationAccess(
            UUID organizationId,
            AuthenticatedUserPrincipal principal,
            Authentication authentication
    ) {
        if (isPlatformAdmin(authentication)) {
            return;
        }

        UUID currentUserId = requireUserId(principal);

        if (!organizationManagementAccess.canManageOrganization(
                organizationId,
                currentUserId
        )) {
            throw new AccessDeniedException(
                    "Нет доступа к созданию событий этой организации"
            );
        }
    }

    private UUID requireUserId(
            AuthenticatedUserPrincipal principal
    ) {
        if (principal == null) {
            throw new BusinessException(
                    "identity.invalid_principal",
                    "Данные авторизованного пользователя недоступны"
            );
        }

        return principal.getUserId();
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        ROLE_PLATFORM_ADMIN.equals(authority.getAuthority())
                );
    }
}