package com.acme.sportplatform.events.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.events.api.EventSessionRequest;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventSessionEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class EventSessionService implements SessionLookup {
    private final EventSessionRepository sessions;
    private final EventAccessService access;
    private final SessionUsageLookup usage;
    private final SessionHeatsLookup heats;

    public EventSessionService(
            EventSessionRepository sessions,
            EventAccessService access,
            SessionUsageLookup usage,
            SessionHeatsLookup heats) {
        this.sessions = sessions;
        this.access = access;
        this.usage = usage;
        this.heats = heats;
    }

    public List<com.acme.sportplatform.events.api.EventSessionResponse> list(
            UUID eventId, com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return views(eventId).stream()
                .map(
                        s -> {
                            var summary = heats.summary(s.id());
                            return new com.acme.sportplatform.events.api.EventSessionResponse(
                                    s.id(),
                                    s.eventId(),
                                    s.name(),
                                    s.orderNo(),
                                    s.startsAt(),
                                    s.breakBetweenEventsSec(),
                                    summary.heatsCount(),
                                    summary.estimatedEndsAt());
                        })
                .toList();
    }

    public List<EventSessionView> views(UUID eventId) {
        return sessions.findByEventIdOrderByOrderNoAsc(eventId).stream().map(this::view).toList();
    }

    public EventSessionView create(
            UUID eventId,
            EventSessionRequest request,
            com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        access.assertCanModify(eventId, principal);
        var entity = new EventSessionEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventId(eventId);
        entity.setCreatedAt(OffsetDateTime.now());
        return save(entity, request);
    }

    public EventSessionView patch(
            UUID id,
            EventSessionRequest request,
            com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        var entity = require(id);
        access.assertCanModify(entity.getEventId(), principal);
        return save(entity, request);
    }

    private EventSessionView save(EventSessionEntity entity, EventSessionRequest r) {
        if (r.name() != null) entity.setName(r.name());
        if (r.orderNo() != null) entity.setOrderNo(r.orderNo());
        if (r.startsAt() != null) entity.setStartsAt(r.startsAt());
        if (r.breakBetweenEventsSec() != null)
            entity.setBreakBetweenEventsSec(r.breakBetweenEventsSec());
        if (entity.getName() == null
                || entity.getName().isBlank()
                || entity.getStartsAt() == null
                || entity.getOrderNo() <= 0)
            throw new BusinessException(
                    "events.invalid_session",
                    "Укажите название, положительный номер и время начала сессии");
        if (sessions.existsByEventIdAndOrderNoAndIdNot(
                entity.getEventId(), entity.getOrderNo(), entity.getId()))
            throw new BusinessException(
                    "events.session_order_conflict", "Сессия с таким номером уже существует");
        entity.setUpdatedAt(OffsetDateTime.now());
        return view(sessions.save(entity));
    }

    public void delete(
            UUID id, com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        var entity = require(id);
        access.assertCanModify(entity.getEventId(), principal);
        if (usage.isInUse(id))
            throw new BusinessException(
                    "events.session_in_use",
                    "Сессия используется дисциплинами и не может быть удалена");
        sessions.delete(entity);
    }

    public void assertBelongsToEvent(UUID id, UUID eventId) {
        if (!require(id).getEventId().equals(eventId))
            throw new BusinessException(
                    "events.session_event_mismatch", "Сессия принадлежит другому событию");
    }

    private EventSessionEntity require(UUID id) {
        return sessions.findById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "events.session_not_found", "Сессия не найдена"));
    }

    private EventSessionView view(EventSessionEntity s) {
        return new EventSessionView(
                s.getId(),
                s.getEventId(),
                s.getName(),
                s.getOrderNo(),
                s.getStartsAt(),
                s.getBreakBetweenEventsSec());
    }
}
