package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.registrations.*;
import com.acme.sportplatform.registrations.api.*;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional
public class ChangeRegistrationUseCase {
    private final RegistrationRepository registrations;
    private final RegistrationMapper mapper;
    private final EventManagementAccess access;
    private final EventLookup events;
    private final ProfileLookup profiles;
    private final ApplicationEventPublisher publisher;
    private final RegistrationLookup lookup;
    private final com.acme.sportplatform.competition.EventDisciplineLookup disciplines;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public ChangeRegistrationUseCase(
            RegistrationRepository registrations,
            RegistrationMapper mapper,
            EventManagementAccess access,
            EventLookup events,
            ProfileLookup profiles,
            ApplicationEventPublisher publisher,
            RegistrationLookup lookup,
            com.acme.sportplatform.competition.EventDisciplineLookup disciplines,
            com.fasterxml.jackson.databind.ObjectMapper json) {
        this.registrations = registrations;
        this.mapper = mapper;
        this.access = access;
        this.events = events;
        this.profiles = profiles;
        this.publisher = publisher;
        this.lookup = lookup;
        this.disciplines = disciplines;
        this.json = json;
    }

    public RegistrationResponse patch(UUID id, PatchRegistrationRequest r) {
        var entity = require(id);
        access.assertCanManage(entity.getEventId());
        if (r.seedTimeMs() != null) entity.setSeedTimeMs(r.seedTimeMs());
        if (r.status() != null) {
            if (!Set.of(RegistrationStatus.ADMITTED, RegistrationStatus.WITHDRAWN)
                    .contains(r.status()))
                throw new BusinessException(
                        "registrations.invalid_status", "Допустимы статусы ADMITTED и WITHDRAWN");
            transition(entity, r.status());
        }
        return save(entity);
    }

    public RegistrationResponse withdraw(UUID id, UUID userId) {
        var entity = require(id);
        if (!access.canManage(entity.getEventId())) {
            assertOwner(entity, userId);
            assertOpen(entity.getEventId());
        }
        transition(entity, RegistrationStatus.WITHDRAWN);
        return save(entity);
    }

    public RegistrationResponse resubmit(
            UUID id, UUID userId, ResubmitRegistrationRequest request) {
        var entity = require(id);
        assertOwner(entity, userId);
        assertOpen(entity.getEventId());
        if (!RegistrationStatus.NEEDS_CORRECTION.name().equals(entity.getStatus()))
            throw new BusinessException(
                    "registrations.invalid_transition",
                    "Повторная подача доступна после запроса исправлений");
        var discipline = disciplines.lockPublishedById(entity.getEventDisciplineId());
        if (discipline.participantLimit() != null
                && registrations.countByEventDisciplineIdAndStatusIn(
                                discipline.id(),
                                List.of(
                                        "SUBMITTED",
                                        "UNDER_REVIEW",
                                        "CONFIRMED",
                                        "PAID",
                                        "ADMITTED"))
                        >= discipline.participantLimit())
            throw new BusinessException(
                    "registrations.discipline_limit_reached", "Достигнут лимит участников");
        var profile =
                request != null && request.profile() != null
                        ? profiles.upsertForUser(userId, request.profile())
                        : profiles.getByUserId(userId);
        String gender =
                switch (profile.gender()) {
                    case "MALE" -> "M";
                    case "FEMALE" -> "F";
                    default -> profile.gender();
                };
        if (discipline.gender() != null
                        && !"X".equals(discipline.gender())
                        && !discipline.gender().equals(gender)
                || !discipline.ageGroups().isEmpty()
                        && discipline.ageGroups().stream()
                                .noneMatch(g -> g.includes(gender, profile.birthDate().getYear())))
            throw new BusinessException(
                    "registrations.athlete_not_eligible",
                    "Пол или год рождения не подходит дисциплине");
        entity.setParticipantSnapshot(json.valueToTree(profile).toString());
        if (request != null) {
            Long seed = request.seedTimeMs();
            if (request.seedTime() != null) {
                long parsed = com.acme.sportplatform.common.TimeParser.parse(request.seedTime());
                if (seed != null && seed != parsed)
                    throw new BusinessException(
                            "registrations.seed_time_mismatch",
                            "Заявочное время в строке и миллисекундах не совпадает");
                seed = parsed;
            }
            if (seed != null) entity.setSeedTimeMs(seed);
        }
        entity.setReviewNote(null);
        entity.setReviewedAt(null);
        entity.setReviewedByUserId(null);
        transition(entity, RegistrationStatus.SUBMITTED);
        entity.setSubmittedAt(OffsetDateTime.now());
        return save(entity);
    }

    public RegistrationResponse markPaid(UUID id) {
        var entity = require(id);
        access.assertCanManage(entity.getEventId());
        lookup.markPaid(id);
        return mapper.toResponse(entity);
    }

    private void transition(RegistrationEntity e, RegistrationStatus target) {
        if (!RegistrationStatus.valueOf(e.getStatus()).canTransition(target))
            throw new BusinessException(
                    "registrations.invalid_transition",
                    "Недопустимый переход статуса заявки: " + e.getStatus() + " → " + target);
        e.setStatus(target.name());
        if (target == RegistrationStatus.WITHDRAWN)
            publisher.publishEvent(
                    new RegistrationWithdrawn(e.getId(), e.getEventId(), e.getEventDisciplineId()));
    }

    private void assertOwner(RegistrationEntity e, UUID userId) {
        if (userId == null
                || !userId.equals(profiles.getById(e.getParticipantProfileId()).userId()))
            throw new AccessDeniedException("Нельзя изменить чужую заявку");
    }

    private void assertOpen(UUID id) {
        var event = events.getById(id);
        var now = OffsetDateTime.now();
        if (!"REGISTRATION_OPEN".equals(event.status())
                || event.registrationOpenAt() != null && now.isBefore(event.registrationOpenAt())
                || event.registrationCloseAt() != null
                        && !now.isBefore(event.registrationCloseAt()))
            throw new BusinessException(
                    "registrations.registration_not_open", "Регистрация закрыта");
    }

    private RegistrationEntity require(UUID id) {
        return registrations
                .lockById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "registrations.not_found", "Заявка не найдена"));
    }

    private RegistrationResponse save(RegistrationEntity e) {
        e.setUpdatedAt(OffsetDateTime.now());
        return mapper.toResponse(registrations.save(e));
    }
}
