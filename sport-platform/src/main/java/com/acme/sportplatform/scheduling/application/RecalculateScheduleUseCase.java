package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.results.CompetitionUnitStore;
import com.acme.sportplatform.scheduling.api.ScheduleResponse;
import com.acme.sportplatform.scheduling.domain.*;
import com.acme.sportplatform.scheduling.infrastructure.RuleExecutionLog;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RecalculateScheduleUseCase {
    private final SchedulingData data;
    private final RuleExecutionLog logs;

    public RecalculateScheduleUseCase(SchedulingData data, RuleExecutionLog logs) {
        this.data = data;
        this.logs = logs;
    }

    @Transactional
    public ScheduleResponse execute(UUID eventId, Authentication principal) {
        data.access.assertCanManage(eventId, principal);
        data.locks.lockForUpdate(eventId);
        long start = System.nanoTime();
        var disciplines = data.disciplines(eventId);
        Map<UUID, List<SeededHeat>> heats = new LinkedHashMap<>();
        List<CompetitionUnitStore.Unit> stored = new ArrayList<>();
        List<DisciplineSpec> specs = new ArrayList<>();
        for (var discipline : disciplines) {
            if (!SchedulingData.isConfigured(discipline)) continue;
            var units = data.units.findByDisciplineId(discipline.id());
            if (units.isEmpty()) continue;
            stored.addAll(units);
            specs.add(data.spec(discipline));
            heats.put(discipline.id(), data.heats(units, data.entries(discipline.id())));
        }
        var sessions = data.sessions(eventId);
        var rules = data.rules(eventId);
        var schedule = new ScheduleBuilder().build(sessions, specs, heats, rules);
        Map<String, ScheduledHeat> indexed = new HashMap<>();
        schedule.forEach(h -> indexed.put(h.disciplineId() + "/" + h.number(), h));
        List<Violation> violations = new ArrayList<>();
        List<ScheduleResponse.Unit> out = new ArrayList<>();
        for (var unit : stored) {
            var scheduled = indexed.get(unit.disciplineId() + "/" + unit.number());
            var time = scheduled == null ? null : scheduled.scheduledAt();
            if (scheduled == null)
                violations.add(
                        new Violation(
                                "NO_SESSION",
                                Violation.Severity.WARNING,
                                "Для дисциплины не задана сессия",
                                unit.disciplineId(),
                                unit.number(),
                                null,
                                null));
            if ("PUBLISHED".equals(unit.status()) && !Objects.equals(unit.scheduledAt(), time))
                violations.add(
                        new Violation(
                                "PUBLISHED_HEAT_CHANGED",
                                Violation.Severity.WARNING,
                                "Время опубликованного заплыва изменено",
                                unit.disciplineId(),
                                unit.number(),
                                null,
                                null));
            data.units.schedule(unit.id(), time);
            out.add(
                    new ScheduleResponse.Unit(
                            unit.id(),
                            unit.number(),
                            time,
                            scheduled == null ? null : scheduled.sequenceInSession()));
        }
        var response = new ScheduleResponse(out, List.copyOf(violations));
        var log =
                logs.begin(
                        eventId,
                        null,
                        "SCHEDULE",
                        Map.of(
                                "sessions",
                                sessions,
                                "disciplines",
                                specs,
                                "heats",
                                heats,
                                "rules",
                                rules),
                        SchedulingData.userId(principal));
        logs.finish(log, response, violations, (System.nanoTime() - start) / 1_000_000);
        return response;
    }
}
