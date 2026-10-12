package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventPublicLookup;
import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.registrations.api.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PublicRegistrationUseCase {
    private final EventPublicLookup events;
    private final EventDisciplineLookup disciplines;
    private final ProfileLookup profiles;
    private final CreateRegistrationUseCase create;

    public PublicRegistrationUseCase(
            EventPublicLookup events,
            EventDisciplineLookup disciplines,
            ProfileLookup profiles,
            CreateRegistrationUseCase create) {
        this.events = events;
        this.disciplines = disciplines;
        this.profiles = profiles;
        this.create = create;
    }

    @Transactional(readOnly = true)
    public RegistrationFormResponse form(String slug) {
        var event = events.getPublicBySlug(slug);
        return new RegistrationFormResponse(
                disciplines.findPublishedByEventId(event.id()).stream()
                        .map(
                                d ->
                                        new RegistrationFormResponse.Discipline(
                                                d.id(),
                                                d.name(),
                                                d.distanceM(),
                                                d.stroke(),
                                                d.gender(),
                                                d.ageGroups(),
                                                d.entryFeeAmount()))
                        .toList(),
                true);
    }

    @Transactional
    public List<RegistrationResponse> submit(
            String slug, UUID userId, PublicRegistrationRequest r) {
        var event = events.getPublicBySlug(slug);
        var now = OffsetDateTime.now();
        if (!"REGISTRATION_OPEN".equals(event.status())
                || event.registrationOpenAt() != null && now.isBefore(event.registrationOpenAt())
                || event.registrationCloseAt() != null
                        && !now.isBefore(event.registrationCloseAt()))
            throw new BusinessException(
                    "registrations.registration_not_open", "Регистрация на событие закрыта");
        var profile = profiles.upsertForUser(userId, r.profile());
        var published =
                disciplines.findPublishedByEventId(event.id()).stream()
                        .collect(java.util.stream.Collectors.toMap(d -> d.id(), d -> d));
        Set<UUID> ids = new HashSet<>();
        List<String> errors = new ArrayList<>();
        // Validate every discipline before persisting any registrations; the surrounding
        // transaction is atomic.
        for (var item : r.items()) {
            if (!ids.add(item.eventDisciplineId())) {
                errors.add(item.eventDisciplineId() + ": дисциплина указана дважды");
                continue;
            }
            var d = published.get(item.eventDisciplineId());
            if (d == null
                    || !d.eventId().equals(event.id())) {
                errors.add(
                        item.eventDisciplineId() + ": дисциплина не опубликована в этом событии");
                continue;
            }
            if (d.gender() != null
                            && !"X".equals(d.gender())
                            && !d.gender().equals(profile.gender())
                    || !d.ageGroups().isEmpty()
                            && d.ageGroups().stream()
                                    .noneMatch(
                                            g ->
                                                    g.includes(
                                                            profile.gender(),
                                                            profile.birthDate().getYear())))
                errors.add(
                        d.name()
                                + ": пол или год рождения не подходит ни под одну возрастную"
                                + " группу");
        }
        if (!errors.isEmpty())
            throw new BusinessException(
                    "registrations.athlete_not_eligible", String.join("; ", errors));
        return r.items().stream()
                .sorted(Comparator.comparing(PublicRegistrationRequest.Item::eventDisciplineId))
                .map(
                        i ->
                                create.execute(
                                        userId,
                                        new CreateRegistrationRequest(
                                                i.eventDisciplineId(), null, i.seedTimeMs(), null)))
                .toList();
    }
}
