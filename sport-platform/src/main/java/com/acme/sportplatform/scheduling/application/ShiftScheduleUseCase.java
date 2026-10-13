package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.results.CompetitionUnitStore;
import com.acme.sportplatform.results.RuleExecutionLog;
import com.acme.sportplatform.scheduling.ScheduleChangedEvent;
import com.acme.sportplatform.scheduling.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class ShiftScheduleUseCase {
    private final SchedulingData data;
    private final RuleExecutionLog logs;
    private final ApplicationEventPublisher events;

    public ShiftScheduleUseCase(SchedulingData data, RuleExecutionLog logs, ApplicationEventPublisher events) {
        this.data = data; this.logs = logs; this.events = events;
    }

    @Transactional
    public ShiftScheduleResponse execute(UUID eventId, ShiftScheduleRequest request,
            AuthenticatedUserPrincipal principal, boolean preview) {
        data.access.assertCanManage(eventId, principal);
        data.locks.lockForUpdate(eventId);
        data.units.lockForEvent(eventId);
        long started = System.nanoTime();
        if (request.deltaSec() == 0) throw invalid("Укажите ненулевой сдвиг расписания");
        var disciplines = data.disciplines(eventId);
        var unitsBySession = new HashMap<UUID, List<CompetitionUnitStore.Unit>>();
        var durations = new HashMap<UUID, Integer>();
        var rules = data.rules(eventId);
        UUID targetSession = null;
        for (var discipline : disciplines) {
            var units = data.units.findByDisciplineId(discipline.id());
            if (units.stream().anyMatch(u -> u.id().equals(request.fromUnitId()))) targetSession = discipline.sessionId();
            if (discipline.sessionId() != null) unitsBySession.computeIfAbsent(discipline.sessionId(), k -> new ArrayList<>()).addAll(units);
            int duration = discipline.heatDurationSec() == null
                    ? discipline.distanceM() == null ? rules.defaultHeatDurationSec()
                            : rules.heatDurationSecByDistance().getOrDefault(discipline.distanceM(), rules.defaultHeatDurationSec())
                    : discipline.heatDurationSec();
            for (var unit : units) durations.put(unit.id(), unit.heatDurationSec() == null ? duration : unit.heatDurationSec());
        }
        if (targetSession == null) throw invalid("Выберите заплыв этого события с назначенной сессией");
        var sessions = data.sessions(eventId).stream().sorted(Comparator.comparingInt(s -> s.orderNo())).toList();
        var zone = data.timezone(eventId);
        var target = targetSession;
        var session = sessions.stream().filter(s -> s.id().equals(target)).findFirst().orElseThrow();
        var day = session.startsAt().atZone(zone).toLocalDate();
        List<ShiftScheduleResponse.Unit> changes = new ArrayList<>();
        boolean inScope = false;
        Instant previousEnd = null;
        for (var current : sessions) {
            if (current.id().equals(target)) inScope = true;
            if (!inScope) continue;
            if (!day.equals(current.startsAt().atZone(zone).toLocalDate())) break;
            var units = unitsBySession.getOrDefault(current.id(), List.of());
            Duration overlap = previousEnd == null ? Duration.ZERO : Duration.between(current.startsAt(), previousEnd);
            long carry = overlap.isNegative() ? 0 : overlap.getSeconds() + (overlap.getNano() == 0 ? 0 : 1);
            long delta = current.id().equals(target) ? request.deltaSec() : carry;
            if (!current.id().equals(target) && delta == 0) break;
            boolean shifting = !current.id().equals(target);
            Instant end = current.startsAt();
            for (var unit : units) {
                if (unit.id().equals(request.fromUnitId())) shifting = true;
                if (unit.scheduledAt() == null) throw invalid("Сначала рассчитайте время всех заплывов сессии");
                Instant time = unit.scheduledAt();
                if (shifting) {
                    if (!Set.of("DRAFT", "SCHEDULED", "PUBLISHED").contains(unit.status()))
                        throw invalid("Нельзя сдвинуть начатый, завершённый или отменённый заплыв");
                    time = time.plusSeconds(delta);
                    if (time.isBefore(end)) throw invalid("Сдвиг пересекает предыдущий заплыв или начало сессии");
                    if (!day.equals(time.plusSeconds(durations.get(unit.id())).atZone(zone).toLocalDate()))
                        throw invalid("Сдвиг выходит за пределы дня площадки");
                    changes.add(new ShiftScheduleResponse.Unit(unit.id(), unit.scheduledAt(), time));
                }
                end = time.plusSeconds(durations.get(unit.id()));
            }
            previousEnd = previousEnd == null || end.isAfter(previousEnd) ? end : previousEnd;
        }
        var response = new ShiftScheduleResponse(List.copyOf(changes), preview ? null : OffsetDateTime.now());
        if (!preview) {
            var log = logs.begin(eventId, null, "SCHEDULE_SHIFT", request, SchedulingData.userId(principal));
            changes.forEach(u -> data.units.schedule(u.id(), u.scheduledAt()));
            logs.finish(log, response, List.of(), (System.nanoTime() - started) / 1_000_000);
            events.publishEvent(new ScheduleChangedEvent(eventId, changes.stream().map(ShiftScheduleResponse.Unit::id).toList(), response.shiftedAt()));
        }
        return response;
    }

    private BusinessException invalid(String message) { return new BusinessException("scheduling.invalid_shift", message); }
}
