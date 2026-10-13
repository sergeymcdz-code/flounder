package com.acme.sportplatform.results.web;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.application.ResultsAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.results.api.AssignRegistrationRequest;
import com.acme.sportplatform.results.api.CompetitionUnitDetailsResponse;
import com.acme.sportplatform.results.api.CompetitionUnitResponse;
import com.acme.sportplatform.results.api.CreateCompetitionUnitRequest;
import com.acme.sportplatform.results.api.PublicCompetitionUnitResultsResponse;
import com.acme.sportplatform.results.api.PublicDisciplineResultsResponse;
import com.acme.sportplatform.results.api.PublishCompetitionUnitRequest;
import com.acme.sportplatform.results.api.RecordResultRequest;
import com.acme.sportplatform.results.api.ResultResponse;
import com.acme.sportplatform.results.application.AssignRegistrationToUnitUseCase;
import com.acme.sportplatform.results.application.CreateCompetitionUnitUseCase;
import com.acme.sportplatform.results.application.GetCompetitionUnitDetailsUseCase;
import com.acme.sportplatform.results.application.GetPublicCompetitionUnitResultsUseCase;
import com.acme.sportplatform.results.application.GetPublicDisciplineResultsUseCase;
import com.acme.sportplatform.results.application.PublishCompetitionUnitUseCase;
import com.acme.sportplatform.results.application.RecalculatePlacesUseCase;
import com.acme.sportplatform.results.application.RecordResultUseCase;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ResultsController {

    private final com.acme.sportplatform.results.application.UpdateCompetitionUnitEntryUseCase updateEntry;
    private final CreateCompetitionUnitUseCase createCompetitionUnitUseCase;
    private final AssignRegistrationToUnitUseCase assignRegistrationToUnitUseCase;
    private final RecordResultUseCase recordResultUseCase;
    private final RecalculatePlacesUseCase recalculatePlacesUseCase;
    private final GetCompetitionUnitDetailsUseCase getCompetitionUnitDetailsUseCase;
    private final PublishCompetitionUnitUseCase publishCompetitionUnitUseCase;
    private final CompetitionUnitRepository competitionUnitRepository;
    private final GetPublicCompetitionUnitResultsUseCase
            getPublicCompetitionUnitResultsUseCase;
    private final GetPublicDisciplineResultsUseCase
            getPublicDisciplineResultsUseCase;

    private final ResultsAccessService resultsAccessService;
    private final com.acme.sportplatform.results.application.SwimResultsUseCase swim;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final jakarta.validation.Validator validator;

    public ResultsController(
            com.acme.sportplatform.results.application.UpdateCompetitionUnitEntryUseCase updateEntry,
            CreateCompetitionUnitUseCase createCompetitionUnitUseCase,
            AssignRegistrationToUnitUseCase assignRegistrationToUnitUseCase,
            RecordResultUseCase recordResultUseCase,
            RecalculatePlacesUseCase recalculatePlacesUseCase,
            GetCompetitionUnitDetailsUseCase getCompetitionUnitDetailsUseCase,
            PublishCompetitionUnitUseCase publishCompetitionUnitUseCase,
            CompetitionUnitRepository competitionUnitRepository,
            GetPublicCompetitionUnitResultsUseCase
                    getPublicCompetitionUnitResultsUseCase,
            GetPublicDisciplineResultsUseCase
                    getPublicDisciplineResultsUseCase,
            ResultsAccessService resultsAccessService,
            com.acme.sportplatform.results.application.SwimResultsUseCase swim,
            com.fasterxml.jackson.databind.ObjectMapper json, jakarta.validation.Validator validator
    ) {
        this.updateEntry = updateEntry;
        this.resultsAccessService = resultsAccessService;
        this.swim=swim;this.json=json;this.validator=validator;
        this.createCompetitionUnitUseCase = createCompetitionUnitUseCase;
        this.assignRegistrationToUnitUseCase = assignRegistrationToUnitUseCase;
        this.recordResultUseCase = recordResultUseCase;
        this.recalculatePlacesUseCase = recalculatePlacesUseCase;
        this.getCompetitionUnitDetailsUseCase = getCompetitionUnitDetailsUseCase;
        this.publishCompetitionUnitUseCase = publishCompetitionUnitUseCase;
        this.competitionUnitRepository = competitionUnitRepository;
        this.getPublicCompetitionUnitResultsUseCase =
                getPublicCompetitionUnitResultsUseCase;
        this.getPublicDisciplineResultsUseCase =
                getPublicDisciplineResultsUseCase;
    }

    /**
     * Список заплывов, матчей, групп или финалов для одной дисциплины.
     * Доступен только внутренним ролям.
     */
    @GetMapping("/event-disciplines/{eventDisciplineId}/competition-units")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or "
            + "hasRole('ORGANIZER') or "
            + "hasRole('OPERATOR') or hasRole('JUDGE')"
    )
    public List<CompetitionUnitResponse> listCompetitionUnits(
            @PathVariable UUID eventDisciplineId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        resultsAccessService.assertDisciplineAccess(eventDisciplineId, principal);

        return competitionUnitRepository
                .findByEventDisciplineIdOrderBySequenceNumberAsc(
                        eventDisciplineId
                )
                .stream()
                .map(unit -> new CompetitionUnitResponse(
                        unit.getId(),
                        unit.getEventDisciplineId(),
                        unit.getLabel(),
                        unit.getSequenceNumber(),
                        unit.getStatus(),
                        unit.getScheduledAt()
                ))
                .toList();
    }

    /**
     * Внутренний просмотр unit с участниками и черновыми результатами.
     */
    @GetMapping("/competition-units/{unitId}")
    @PreAuthorize(
            "hasRole('PLATFORM_ADMIN') or "
            + "hasRole('ORGANIZER') or "
            + "hasRole('OPERATOR') or hasRole('JUDGE')"
    )
    public CompetitionUnitDetailsResponse getUnitDetails(
            @PathVariable UUID unitId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        resultsAccessService.assertUnitAccess(unitId, principal);

        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    /**
     * Публичная итоговая таблица одной опубликованной единицы соревнования.
     * Черновик намеренно выглядит как отсутствующий ресурс.
     */
    @GetMapping("/public/competition-units/{unitId}/results")
    public PublicCompetitionUnitResultsResponse getPublicResults(
            @PathVariable UUID unitId
    ) {
        return getPublicCompetitionUnitResultsUseCase.execute(unitId);
    }

    /**
     * Публичные результаты всех опубликованных unit конкретной дисциплины.
     *
     * eventId и eventDisciplineId сверяются, чтобы нельзя было запросить
     * дисциплину одного события через URL другого события.
     */
    @GetMapping(
            "/public/events/{eventId}/disciplines/{eventDisciplineId}/results"
    )
    public PublicDisciplineResultsResponse getPublicDisciplineResults(
            @PathVariable UUID eventId,
            @PathVariable UUID eventDisciplineId
    ) {
        return getPublicDisciplineResultsUseCase.execute(
                eventId,
                eventDisciplineId
        );
    }

    /**
     * Организатор создаёт заплыв, матч, группу, финал или иной unit.
     */
    @PostMapping("/competition-units")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')"
    )
    public ResponseEntity<CompetitionUnitResponse> createCompetitionUnit(
            @Valid @RequestBody CreateCompetitionUnitRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        resultsAccessService.assertDisciplineAccess(request.eventDisciplineId(), principal);

        CompetitionUnitResponse response =
                createCompetitionUnitUseCase.execute(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Организатор назначает подтверждённую заявку в unit.
     */
    @PostMapping("/competition-units/{unitId}/entries")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR')"
    )
    public ResponseEntity<UUID> assignRegistration(
            @PathVariable UUID unitId,
            @Valid @RequestBody AssignRegistrationRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        resultsAccessService.assertUnitAccess(unitId, principal);

        UUID entryId = assignRegistrationToUnitUseCase.execute(
                unitId,
                request.registrationId(),
                request.laneOrPosition()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(entryId);
    }

    /**
     * Оператор вносит или обновляет результат одной entry.
     */
    @PostMapping("/results")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR', 'JUDGE')"
    )
    public ResultResponse recordResult(
            @AuthenticationPrincipal(expression = "userId")
            UUID recordedByUserId,
            @Valid @RequestBody RecordResultRequest request,
            @RequestParam String resultType,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        resultsAccessService.assertEntryAccess(request.competitionUnitEntryId(), principal);

        return recordResultUseCase.execute(
                request,
                resultType,
                recordedByUserId
        );
    }

    /**
     * Сохраняет результат и сразу пересчитывает места в конкретном unit.
     */
    @PostMapping("/competition-units/{unitId}/results")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR', 'JUDGE')"
    )
    @io.swagger.v3.oas.annotations.Operation(summary="Ввод результатов заплыва", requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(
            content=@io.swagger.v3.oas.annotations.media.Content(array=@io.swagger.v3.oas.annotations.media.ArraySchema(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=com.acme.sportplatform.results.api.SwimResultRequest.class)))))
    public CompetitionUnitDetailsResponse recordResultAndRecalculate(
            @PathVariable UUID unitId,
            @AuthenticationPrincipal(expression = "userId") UUID recordedByUserId,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body,
            @RequestParam(defaultValue = "TIME") String resultType,
            @RequestParam(defaultValue = "ASC") String rankingStrategy,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        resultsAccessService.assertUnitAccess(unitId,principal);
        if (body.isArray()) {
            var errors =
                    new
            java.util.ArrayList<
                            com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation>();
            var requests =
                    new java.util.ArrayList<com.acme.sportplatform.results.api.SwimResultRequest>();
            if (body.isEmpty())
                errors.add(
                        new com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation(
                                "items", "Укажите результаты участников"));
            for (int i = 0; i < body.size(); i++)
                requests.add(
                        com.acme.sportplatform.common.RequestValidation.read(
                                json,
                                body.get(i),
                                                com.acme.sportplatform.results.api
                                                        .SwimResultRequest.class,
                                validator,
                                "[" + i + "].",
                                errors));
            com.acme.sportplatform.common.RequestValidation.requireValid(
                    "results.invalid_request", errors);
            return swim.save(unitId, recordedByUserId, requests,principal);
        }
        RecordResultRequest request;
        try {
            request = json.convertValue(body, RecordResultRequest.class);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    "results.invalid_request", "Некорректный формат результата");
        }
        if (request == null || request.competitionUnitEntryId() == null)
            throw new BusinessException("results.invalid_request", "Укажите участника заплыва");
        resultsAccessService.assertEntryAccess(request.competitionUnitEntryId(),principal);
        recordResultUseCase.execute(request, resultType, recordedByUserId, unitId);
        recalculatePlacesUseCase.execute(unitId, rankingStrategy);
        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    /**
     * Ручной пересчёт мест после внесения нескольких результатов.
     */
    @PostMapping("/competition-units/{unitId}/recalculate")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER', 'OPERATOR', 'JUDGE')"
    )
    public CompetitionUnitDetailsResponse recalculate(
            @PathVariable UUID unitId,
            @RequestParam String rankingStrategy,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        resultsAccessService.assertUnitAccess(unitId, principal);

        recalculatePlacesUseCase.execute(
                unitId,
                rankingStrategy
        );

        return getCompetitionUnitDetailsUseCase.execute(unitId);
    }

    /**
     * Publishes complete results: PUBLISHED/IN_PROGRESS → FINISHED, without moving live.
     * Unpublishes results: FINISHED → PUBLISHED; only organizer/operator/admin may do this.
     */
    @PostMapping("/competition-units/{unitId}/publication")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN', 'OPERATOR', 'ORGANIZER', 'JUDGE')"
    )
    public ResponseEntity<Void> changePublication(
            @PathVariable UUID unitId,
            @RequestBody PublishCompetitionUnitRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        resultsAccessService.assertUnitAccess(unitId, principal);

        if (!request.published() && principal.getAuthorities().stream().noneMatch(a ->
                java.util.Set.of("ROLE_ORGANIZER", "ROLE_OPERATOR", "ROLE_PLATFORM_ADMIN")
                        .contains(a.getAuthority()))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Снятие опубликованных результатов недоступно судье");
        }

        publishCompetitionUnitUseCase.execute(
                unitId,
                request.published()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/event-disciplines/{id}/results")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR','JUDGE')")
    public com.acme.sportplatform.results.api.DisciplineResultsResponse protocol(@PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        resultsAccessService.assertDisciplineAccess(id,principal);
        return swim.protocol(id,principal);
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORGANIZER','OPERATOR')")
    @org.springframework.web.bind.annotation.PatchMapping("/competition-unit-entries/{id}")
    public ResponseEntity<Void> updateEntry(@PathVariable UUID id,
            @Valid @RequestBody com.acme.sportplatform.results.api.UpdateCompetitionUnitEntryRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        updateEntry.execute(id, request.laneOrPosition(), principal);
        return ResponseEntity.noContent().build();
    }

}
