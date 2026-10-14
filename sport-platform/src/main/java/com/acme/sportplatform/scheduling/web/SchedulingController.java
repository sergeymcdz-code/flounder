package com.acme.sportplatform.scheduling.web;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.scheduling.api.*;
import com.acme.sportplatform.scheduling.application.*;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Посев и расписание")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@org.springframework.security.access.prepost.PreAuthorize(
        "hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
@RestController
@RequestMapping("/api/v1")
public class SchedulingController {
    private final GenerateCompetitionUnitsUseCase generate;
    private final GenerateForEventUseCase generateEvent;
    private final RecalculateScheduleUseCase recalculate;
    private final ValidateUnitsUseCase validate;
    private final PublishUnitsUseCase publish;
    private final ShiftScheduleUseCase shift;

    public SchedulingController(
            GenerateCompetitionUnitsUseCase generate,
            GenerateForEventUseCase generateEvent,
            RecalculateScheduleUseCase recalculate,
            ValidateUnitsUseCase validate,
            PublishUnitsUseCase publish,
            ShiftScheduleUseCase shift) {
        this.generate = generate;
        this.generateEvent = generateEvent;
        this.recalculate = recalculate;
        this.validate = validate;
        this.publish = publish;
        this.shift = shift;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Сформировать заплывы дисциплины")
    @PostMapping("/event-disciplines/{id}/generate-competition-units")
    public GenerationResponse generate(
            @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @io.swagger.v3.oas.annotations.media.Content(
                                            examples =
                                                    @io.swagger.v3.oas.annotations.media
                                                            .ExampleObject(
                                                            value =
                                                                    "{\"force\":false,\"circleSeedTopHeats\":false}")))
                    @RequestBody(required = false)
                    GenerateRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    AuthenticatedUserPrincipal principal) {
        var options = request == null ? GenerateRequest.defaults() : request;
        return generate.execute(id, options.force(), options.circleSeedTopHeats(), principal);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Сформировать заплывы всего события")
    @PostMapping("/events/{eventId}/generate-competition-units")
    public EventGenerationResponse generateEvent(
            @PathVariable UUID eventId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @io.swagger.v3.oas.annotations.media.Content(
                                            examples =
                                                    @io.swagger.v3.oas.annotations.media
                                                            .ExampleObject(
                                                            value =
                                                                    "{\"force\":false,\"circleSeedTopHeats\":false}")))
                    @RequestBody(required = false)
                    GenerateRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    AuthenticatedUserPrincipal principal) {
        var options = request == null ? GenerateRequest.defaults() : request;
        return generateEvent.execute(
                eventId, options.force(), options.circleSeedTopHeats(), principal);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Пересчитать расписание события")
    @PostMapping("/events/{eventId}/schedule/recalculate")
    public ScheduleResponse recalculate(
            @PathVariable UUID eventId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    AuthenticatedUserPrincipal principal) {
        return recalculate.execute(eventId, principal);
    }

    @PostMapping("/events/{eventId}/schedule/shift")
    public ShiftScheduleResponse shift(@PathVariable UUID eventId,
            @jakarta.validation.Valid @RequestBody ShiftScheduleRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return shift.execute(eventId, request, principal, false);
    }

    @PostMapping("/events/{eventId}/schedule/shift/preview")
    public ShiftScheduleResponse previewShift(@PathVariable UUID eventId,
            @jakarta.validation.Valid @RequestBody ShiftScheduleRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return shift.execute(eventId, request, principal, true);
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Проверить назначения независимым валидатором")
    @GetMapping("/event-disciplines/{id}/competition-units/validate")
    public ValidationResponse validate(
            @PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    AuthenticatedUserPrincipal principal) {
        return validate.execute(id, principal);
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Опубликовать заплывы дисциплины после проверки")
    @PostMapping("/event-disciplines/{id}/competition-units/publish")
    public ValidationResponse publish(
            @PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    AuthenticatedUserPrincipal principal) {
        return publish.forDiscipline(id, principal);
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Опубликовать заплывы всего события после проверки")
    @PostMapping("/events/{eventId}/competition-units/publish")
    public ValidationResponse publishEvent(
            @PathVariable UUID eventId,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    AuthenticatedUserPrincipal principal) {
        return publish.forEvent(eventId, principal);
    }
}
