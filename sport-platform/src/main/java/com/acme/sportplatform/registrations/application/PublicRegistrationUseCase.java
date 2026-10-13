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
    private final RegistrationCapacityService capacity;
    private final com.acme.sportplatform.events.EventLookup eventLookup;
    private final com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository registrations;

    public PublicRegistrationUseCase(
            EventPublicLookup events,
            EventDisciplineLookup disciplines,
            ProfileLookup profiles,
            CreateRegistrationUseCase create, com.acme.sportplatform.events.EventLookup eventLookup,
            com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository registrations, RegistrationCapacityService capacity) {
        this.events = events;
        this.disciplines = disciplines;
        this.profiles = profiles;
        this.create = create;
        this.eventLookup = eventLookup; this.registrations = registrations; this.capacity = capacity;
    }

    @Transactional(readOnly = true)
    public RegistrationFormResponse form(String slug) {
        var event = events.getPublicBySlug(slug);
        var details = eventLookup.getById(event.id());
        var active = registrations.findByEventId(event.id()).stream()
                .filter(r -> Set.of("SUBMITTED", "UNDER_REVIEW", "CONFIRMED", "PAID", "ADMITTED").contains(r.getStatus())).toList();
        var available = disciplines.findPublishedByEventId(event.id()).stream().map(d -> {
            var limits = disciplines.getForScheduling(d.id());
            Integer maximum = limits.maxParticipants();
            if (d.participantLimit() != null)
                maximum = maximum == null ? d.participantLimit() : Math.min(maximum, d.participantLimit());
            return new RegistrationFormResponse.Discipline(d.id(), d.name(), d.distanceM(), d.stroke(),
                    d.gender(), d.ageGroups(), d.entryFeeAmount(), maximum,
                    active.stream().filter(r -> r.getEventDisciplineId().equals(d.id())).count(),
                    limits.limitsByAgeGroup(), capacity.groupCounts(active, limits));
        }).toList();
        return new RegistrationFormResponse(available, true, isOpen(event), details.maxParticipants(),
                active.stream().map(r -> r.getParticipantProfileId()).distinct().count());
    }

    @Transactional
    public List<RegistrationResponse> submit(
            String slug, UUID userId, PublicRegistrationRequest r) {
        var event = events.getPublicBySlug(slug);
        if (!isOpen(event))
            throw new BusinessException("registrations.registration_closed", "Регистрация на событие закрыта");
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
    private boolean isOpen(com.acme.sportplatform.events.EventPublicLookupResult event) {
        var now = OffsetDateTime.now();
        return "REGISTRATION_OPEN".equals(event.status())
                && (event.registrationOpenAt() == null || !now.isBefore(event.registrationOpenAt()))
                && (event.registrationCloseAt() == null || now.isBefore(event.registrationCloseAt()));
    }

}
