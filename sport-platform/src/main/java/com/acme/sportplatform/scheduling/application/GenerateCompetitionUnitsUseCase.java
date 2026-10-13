package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.competition.EventDisciplineLookupResult;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.results.CompetitionUnitStore;
import com.acme.sportplatform.results.RuleExecutionLog;
import com.acme.sportplatform.scheduling.api.*;
import com.acme.sportplatform.scheduling.domain.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GenerateCompetitionUnitsUseCase {
    private final SchedulingData data;
    private final RuleExecutionLog logs;
    private final RecalculateScheduleUseCase recalculate;

    public GenerateCompetitionUnitsUseCase(
            SchedulingData data, RuleExecutionLog logs, RecalculateScheduleUseCase recalculate) {
        this.data = data;
        this.logs = logs;
        this.recalculate = recalculate;
    }

    @Transactional
    public GenerationResponse execute(
            UUID disciplineId,
            boolean force,
            boolean circleSeedTopHeats,
            AuthenticatedUserPrincipal principal) {
        Draft draft = prepare(data.discipline(disciplineId), force, circleSeedTopHeats, principal);
        var schedule = recalculate.execute(draft.eventId(), principal);
        return complete(draft, schedule.violations());
    }

    record Draft(
            UUID eventId,
            UUID disciplineId,
            UUID runId,
            List<SeedEntry> entries,
            List<Violation> violations,
            List<UUID> excludedRegistrationIds,
            long startedAtNanos) {}

    // The caller owns the transaction and performs one schedule recalculation after all drafts.
    Draft prepare(
            EventDisciplineLookupResult discipline,
            boolean force,
            boolean circleSeedTopHeats,
            AuthenticatedUserPrincipal principal) {
        UUID disciplineId = discipline.id();
        data.access.assertCanManage(discipline.eventId(), principal);
        data.locks.lockForUpdate(discipline.eventId());
        long start = System.nanoTime();
        var previousUnits = data.units.findByDisciplineId(disciplineId);
        boolean published = previousUnits.stream().anyMatch(u -> "PUBLISHED".equals(u.status()));
        var spec = data.spec(discipline);
        var pool = data.pool(discipline.eventId());
        var entries = data.entries(disciplineId);
        var options = new SeedingOptions(circleSeedTopHeats);
        var seeded = new HeatSeeder().seed(spec, entries, pool, options);
        List<Violation> violations = new ArrayList<>(seeded.violations());
        if (published)
            violations.add(
                    new Violation(
                            "PUBLISHED_HEAT_CHANGED",
                            Violation.Severity.WARNING,
                            "Опубликованные заплывы пересозданы по force=true",
                            disciplineId,
                            null,
                            null,
                            null));
        UUID runId =
                logs.begin(
                        discipline.eventId(),
                        disciplineId,
                        "SEEDING",
                        Map.of(
                                "discipline",
                                spec,
                                "pool",
                                pool,
                                "entries",
                                entries,
                                "options",
                                options,
                                "force",
                                force,
                                "previousUnits",
                                previousUnits),
                        SchedulingData.userId(principal));
        var rules = data.rules(discipline.eventId());
        List<CompetitionUnitStore.Unit> units = new ArrayList<>();
        for (SeededHeat heat : seeded.heats()) {
            List<CompetitionUnitStore.Entry> lanes = new ArrayList<>();
            for (SeededLane lane : heat.lanes()) {
                lanes.add(
                        new CompetitionUnitStore.Entry(
                                UUID.randomUUID(),
                                lane.lane(),
                                lane.registrationId(),
                                lane.seedTimeMs(),
                                "ASSIGNED"));
            }
            units.add(
                    new CompetitionUnitStore.Unit(
                            UUID.randomUUID(),
                            disciplineId,
                            heat.number(),
                            heat.ageGroupCode(),
                            "DRAFT",
                            rules.duration(spec),
                            null,
                            lanes));
        }
        data.units.replace(disciplineId, force, units, runId);
        return new Draft(
                discipline.eventId(),
                disciplineId,
                runId,
                entries,
                List.copyOf(violations),
                seeded.excludedRegistrationIds(),
                start);
    }

    GenerationResponse complete(Draft draft, List<Violation> scheduleViolations) {
        List<Violation> violations = new ArrayList<>(draft.violations());
        violations.addAll(scheduleViolations);
        Map<UUID, SeedEntry> indexed = new HashMap<>();
        draft.entries().forEach(e -> indexed.put(e.registrationId(), e));
        List<GenerationResponse.Heat> heats = new ArrayList<>();
        for (var unit : data.units.findByDisciplineId(draft.disciplineId())) {
            List<GenerationResponse.Lane> lanes = new ArrayList<>();
            for (var lane : unit.entries()) {
                SeedEntry entry = indexed.get(lane.registrationId());
                var athlete =
                        new GenerationResponse.Athlete(
                                entry.lastName(),
                                entry.firstName(),
                                entry.birthYear(),
                                entry.club());
                lanes.add(
                        new GenerationResponse.Lane(
                                lane.lane(), lane.registrationId(), athlete, lane.seedTimeMs()));
            }
            heats.add(
                    new GenerationResponse.Heat(
                            unit.id(),
                            unit.number(),
                            unit.ageGroupCode(),
                            unit.scheduledAt(),
                            lanes));
        }
        long duration = (System.nanoTime() - draft.startedAtNanos()) / 1_000_000;
        var response =
                new GenerationResponse(
                        draft.runId(),
                        heats,
                        List.copyOf(violations),
                        draft.excludedRegistrationIds(),
                        duration);
        logs.finish(draft.runId(), response, violations, duration);
        return response;
    }
}
