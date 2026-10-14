package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.scheduling.api.EventGenerationResponse;
import com.acme.sportplatform.scheduling.api.GenerationResponse;
import com.acme.sportplatform.scheduling.domain.Violation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GenerateForEventUseCase {
    private final SchedulingData data;
    private final GenerateCompetitionUnitsUseCase generate;
    private final RecalculateScheduleUseCase recalculate;

    public GenerateForEventUseCase(
            SchedulingData data,
            GenerateCompetitionUnitsUseCase generate,
            RecalculateScheduleUseCase recalculate) {
        this.data = data;
        this.generate = generate;
        this.recalculate = recalculate;
    }

    @Transactional
    public EventGenerationResponse execute(
            UUID eventId,
            boolean force,
            boolean circleSeedTopHeats,
            AuthenticatedUserPrincipal principal) {
        data.access.assertCanModify(eventId, principal);
        data.locks.lockWritableForUpdate(eventId);
        long start = System.nanoTime();
        var disciplines = data.disciplines(eventId);
        Map<UUID, GenerateCompetitionUnitsUseCase.Draft> drafts = new LinkedHashMap<>();
        Map<UUID, GenerationResponse> results = new HashMap<>();
        Set<Violation> violations = new LinkedHashSet<>();
        for (var discipline : disciplines) {
            if (!SchedulingData.isConfigured(discipline)) {
                var warning =
                        new Violation(
                                "DISCIPLINE_NOT_CONFIGURED",
                                Violation.Severity.WARNING,
                                "Вид «"
                                        + discipline.name()
                                        + "» пропущен: не указаны дистанция, стиль или пол",
                                discipline.id(),
                                null,
                                null,
                                null);
                violations.add(warning);
                results.put(
                        discipline.id(),
                        new GenerationResponse(null, List.of(), List.of(warning), List.of(), 0));
                continue;
            }
            drafts.put(
                    discipline.id(),
                    generate.prepare(discipline, force, circleSeedTopHeats, principal));
        }
        var schedule = recalculate.execute(eventId, principal);
        violations.addAll(schedule.violations());
        for (var draft : drafts.values()) {
            var result =
                    generate.complete(
                            draft,
                            schedule.violations().stream()
                                    .filter(v -> draft.disciplineId().equals(v.eventDisciplineId()))
                                    .toList());
            results.put(draft.disciplineId(), result);
            violations.addAll(result.violations());
        }
        var out =
                disciplines.stream()
                        .map(
                                d ->
                                        new EventGenerationResponse.Discipline(
                                                d.id(), results.get(d.id())))
                        .toList();
        return new EventGenerationResponse(
                out, List.copyOf(violations), (System.nanoTime() - start) / 1_000_000);
    }
}
