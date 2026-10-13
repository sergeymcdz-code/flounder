package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.results.RuleExecutionLog;
import com.acme.sportplatform.scheduling.api.ValidationResponse;
import com.acme.sportplatform.scheduling.domain.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ValidateUnitsUseCase {
    private final SchedulingData data;
    private final RuleExecutionLog logs;

    public ValidateUnitsUseCase(SchedulingData data, RuleExecutionLog logs) {
        this.data = data;
        this.logs = logs;
    }

    @Transactional
    public ValidationResponse execute(UUID disciplineId, AuthenticatedUserPrincipal principal) {
        var discipline = data.discipline(disciplineId);
        data.access.assertCanManage(discipline.eventId(), principal);
        long start = System.nanoTime();
        var spec = data.spec(discipline);
        var pool = data.pool(discipline.eventId());
        var entries = data.entries(disciplineId);
        var stored = data.units.findByDisciplineId(disciplineId);
        var heats = data.heats(stored, entries);
        var schedule = data.schedule(discipline.eventId());
        var rules = data.rules(discipline.eventId());
        var violations = new ArrayList<>(
                new SeedingValidator().validate(spec, pool, entries, heats, schedule, rules));
        for (var unit : stored)
            violations.addAll(SchedulingData.publishedWithdrawalViolations(unit));
        var response = new ValidationResponse(violations);
        UUID runId =
                logs.begin(
                        discipline.eventId(),
                        disciplineId,
                        "VALIDATION",
                        Map.of(
                                "discipline",
                                spec,
                                "pool",
                                pool,
                                "entries",
                                entries,
                                "heats",
                                heats,
                                "schedule",
                                schedule,
                                "rules",
                                rules),
                        SchedulingData.userId(principal));
        logs.finish(runId, response, violations, (System.nanoTime() - start) / 1_000_000);
        return response;
    }
}
