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
    private final RegistrationCapacityService capacity;
    private final RegistrationMapper mapper;
    private final EventAccessService access;
    private final EventLookup events;
    private final ProfileLookup profiles;
    private final ApplicationEventPublisher publisher;
    private final com.acme.sportplatform.competition.EventDisciplineLookup disciplines;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public ChangeRegistrationUseCase(
            RegistrationRepository registrations,
            RegistrationMapper mapper,
            EventAccessService access,
            EventLookup events,
            ProfileLookup profiles,
            ApplicationEventPublisher publisher,
            com.acme.sportplatform.competition.EventDisciplineLookup disciplines,
            com.fasterxml.jackson.databind.ObjectMapper json, RegistrationCapacityService capacity) {
        this.registrations = registrations;
        this.mapper = mapper;
        this.access = access;
        this.events = events;
        this.profiles = profiles;
        this.publisher = publisher;
        this.disciplines = disciplines;
        this.json = json;
        this.capacity = capacity;
    }

    public RegistrationResponse patch(
            UUID id,
            PatchRegistrationRequest r,
            com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        var entity = require(id);
        access.assertCanModify(entity.getEventId(), principal);
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

    public RegistrationResponse withdraw(
            UUID id, com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        var entity = require(id);
        boolean manager =
                principal != null
                        && principal.getAuthorities().stream()
                                .anyMatch(
                                        authority ->
                                                java.util.Set.of(
                                                                "ROLE_PLATFORM_ADMIN",
                                                                "ROLE_ORGANIZER",
                                                                "ROLE_OPERATOR")
                                                        .contains(authority.getAuthority()));
        if (!manager || !access.canManage(entity.getEventId(), principal)) {
            assertOwner(entity, principal.getUserId());
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
        capacity.allocate(entity, "SUBMITTED");
        entity.setSubmittedAt(OffsetDateTime.now());
        var response = save(entity);
        publisher.publishEvent(new RegistrationSubmittedEvent(id));
        return response;
    }

    private void transition(RegistrationEntity e, RegistrationStatus target) {
        if (!RegistrationStatus.valueOf(e.getStatus()).canTransition(target))
            throw new BusinessException(
                    "registrations.invalid_transition",
                    "Недопустимый переход статуса заявки: " + e.getStatus() + " → " + target);
        e.setStatus(target.name());
        e.setWaitlistPosition(null);
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
                    "registrations.registration_closed", "Регистрация закрыта");
    }

    private RegistrationEntity require(UUID id) {
        var selectedEventId = registrations.findEventIdById(id).orElseThrow(() ->
                new BusinessException("registrations.not_found", "Заявка не найдена"));
        capacity.lock(selectedEventId);
        access.assertWritable(selectedEventId);
        return registrations
                .lockById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "registrations.not_found", "Заявка не найдена"));
    }

    private RegistrationResponse save(RegistrationEntity e) {
        e.setUpdatedAt(OffsetDateTime.now());
        registrations.saveAndFlush(e);
        if ("WITHDRAWN".equals(e.getStatus())) capacity.promote(e.getEventId());
        return mapper.toResponse(e);
    }
}
