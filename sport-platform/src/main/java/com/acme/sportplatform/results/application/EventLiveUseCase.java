package com.acme.sportplatform.results.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventAccessService;
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
    private final EventAccessService access;

    public EventLiveUseCase(
            EventLiveStateRepository live,
            CompetitionUnitRepository units,
            EventDisciplineLookup disciplines,
            EventAccessService access) {
        this.live = live;
        this.units = units;
        this.disciplines = disciplines;
        this.access = access;
    }

    public LiveStateResponse set(
            UUID eventId,
            UUID currentUnitId,
            com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        if (currentUnitId != null && !eventId.equals(eventId(require(currentUnitId))))
            throw new BusinessException(
                    "results.unit_event_mismatch", "Заплыв принадлежит другому событию");
        var state = lockState(eventId);
        state.setCurrentUnitId(currentUnitId);
        state.setUpdatedAt(OffsetDateTime.now());
        return new LiveStateResponse(eventId, currentUnitId, state.getUpdatedAt());
    }

    public LiveStateResponse start(
            UUID id, com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        var unit = require(id);
        UUID eventId = eventId(unit);
        access.assertCanManage(eventId, principal);
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
        return set(eventId, id, principal);
    }

    public LiveStateResponse finish(
            UUID id, com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        var unit = require(id);
        UUID eventId = eventId(unit);
        access.assertCanManage(eventId, principal);
        lockState(eventId);
        if (!CompetitionUnitStatus.IN_PROGRESS.name().equals(unit.getStatus()))
            throw new BusinessException(
                    "results.invalid_transition", "Завершить можно только начатый заплыв");
        unit.setStatus(CompetitionUnitStatus.FINISHED.name());
        unit.setUpdatedAt(OffsetDateTime.now());
        UUID next =
                units.nextScheduled(
                                eventId,
                                unit.getScheduledAt(),
                                unit.getSequenceNumber(),
                                unit.getId())
                        .map(CompetitionUnitEntity::getId)
                        .orElse(null);
        return set(eventId, next, principal);
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
