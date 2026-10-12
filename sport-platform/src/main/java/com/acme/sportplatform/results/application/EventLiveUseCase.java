package com.acme.sportplatform.results.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventManagementAccess;
import com.acme.sportplatform.results.api.*;
import com.acme.sportplatform.results.domain.CompetitionUnitStatus;
import com.acme.sportplatform.results.infrastructure.jpa.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class EventLiveUseCase {
    private final EventLiveStateRepository live;
    private final CompetitionUnitRepository units;
    private final EventDisciplineLookup disciplines;
    private final EventManagementAccess access;

    public EventLiveUseCase(
            EventLiveStateRepository live,
            CompetitionUnitRepository units,
            EventDisciplineLookup disciplines,
            EventManagementAccess access) {
        this.live = live;
        this.units = units;
        this.disciplines = disciplines;
        this.access = access;
    }

    public LiveStateResponse set(UUID eventId, UUID currentUnitId) {
        access.assertCanManage(eventId);
        if (currentUnitId != null && !eventId.equals(eventId(require(currentUnitId))))
            throw new BusinessException(
                    "results.unit_event_mismatch", "Заплыв принадлежит другому событию");
        var state = lockState(eventId);
        state.setCurrentUnitId(currentUnitId);
        state.setUpdatedAt(OffsetDateTime.now());
        return new LiveStateResponse(eventId, currentUnitId, state.getUpdatedAt());
    }

    public LiveStateResponse start(UUID id) {
        var unit = require(id);
        UUID eventId = eventId(unit);
        access.assertCanManage(eventId);
        if (!Set.of("DRAFT", "SCHEDULED", "PUBLISHED").contains(unit.getStatus()))
            throw new BusinessException(
                    "results.invalid_transition", "Заплыв нельзя начать из текущего статуса");
        var state = lockState(eventId);
        if (state != null
                && state.getCurrentUnitId() != null
                && !state.getCurrentUnitId().equals(id)
                && "IN_PROGRESS".equals(require(state.getCurrentUnitId()).getStatus()))
            throw new BusinessException(
                    "results.unit_in_progress", "Другой заплыв ещё не завершён");
        unit.setStatus(CompetitionUnitStatus.IN_PROGRESS.name());
        unit.setUpdatedAt(OffsetDateTime.now());
        return set(eventId, id);
    }

    public LiveStateResponse finish(UUID id) {
        var unit = require(id);
        UUID eventId = eventId(unit);
        access.assertCanManage(eventId);
        lockState(eventId);
        if (!CompetitionUnitStatus.IN_PROGRESS.name().equals(unit.getStatus()))
            throw new BusinessException(
                    "results.invalid_transition", "Завершить можно только начатый заплыв");
        unit.setStatus(CompetitionUnitStatus.FINISHED.name());
        unit.setUpdatedAt(OffsetDateTime.now());
        Comparator<CompetitionUnitEntity> order =
                Comparator.comparing(
                                CompetitionUnitEntity::getScheduledAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CompetitionUnitEntity::getSequenceNumber)
                        .thenComparing(CompetitionUnitEntity::getId);
        List<CompetitionUnitEntity> all =
                disciplines.findByEventId(eventId).stream()
                        .flatMap(
                                d ->
                                        units
                                                .findByEventDisciplineIdOrderBySequenceNumberAsc(
                                                        d.id())
                                                .stream())
                        .sorted(order)
                        .toList();
        UUID next =
                all.stream()
                        .filter(u -> order.compare(u, unit) > 0)
                        .filter(
                                u ->
                                        Set.of("DRAFT", "SCHEDULED", "PUBLISHED")
                                                .contains(u.getStatus()))
                        .map(CompetitionUnitEntity::getId)
                        .findFirst()
                        .orElse(null);
        return set(eventId, next);
    }

    private EventLiveStateEntity lockState(UUID eventId) {
        live.ensureEvent(eventId);
        return live.lockForEvent(eventId).orElseThrow();
    }

    private CompetitionUnitEntity require(UUID id) {
        return units.findById(id)
                .orElseThrow(
                        () -> new BusinessException("results.unit_not_found", "Заплыв не найден"));
    }

    private UUID eventId(CompetitionUnitEntity unit) {
        return disciplines
                .findById(unit.getEventDisciplineId())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "competition.discipline_not_found",
                                        "Дисциплина не найдена"))
                .eventId();
    }
}
