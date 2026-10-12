package com.acme.sportplatform.results.web;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
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
    private final EventDisciplineLookup eventDisciplineLookup;

    private final ResultsAccessService resultsAccessService;

    public ResultsController(
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
            EventDisciplineLookup eventDisciplineLookup,
            ResultsAccessService resultsAccessService
    ) {
        this.resultsAccessService = resultsAccessService;
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
        this.eventDisciplineLookup = eventDisciplineLookup;
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
        EventDisciplineSummary discipline = eventDisciplineLookup
                .findById(eventDisciplineId)
                .orElseThrow(this::publicResultsNotFound);

        if (!eventId.equals(discipline.eventId())) {
            throw publicResultsNotFound();
        }

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
    public CompetitionUnitDetailsResponse recordResultAndRecalculate(
            @PathVariable UUID unitId,
            @AuthenticationPrincipal(expression = "userId")
            UUID recordedByUserId,
            @Valid @RequestBody RecordResultRequest request,
            @RequestParam String resultType,
            @RequestParam(defaultValue = "ASC") String rankingStrategy,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        resultsAccessService.assertUnitAccess(unitId, principal);
        resultsAccessService.assertEntryAccess(request.competitionUnitEntryId(), principal);

        recordResultUseCase.execute(
                request,
                resultType,
                recordedByUserId,
                unitId
        );

        recalculatePlacesUseCase.execute(
                unitId,
                rankingStrategy
        );

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
     * Публикует результаты либо возвращает unit в статус DRAFT.
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

        publishCompetitionUnitUseCase.execute(
                unitId,
                request.published()
        );

        return ResponseEntity.noContent().build();
    }

    private BusinessException publicResultsNotFound() {
        return new BusinessException(
                "results.public_not_found",
                "Опубликованные результаты не найдены"
        );
    }
}