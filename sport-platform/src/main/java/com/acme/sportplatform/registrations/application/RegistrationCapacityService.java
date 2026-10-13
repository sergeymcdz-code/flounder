package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineLookupResult;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventLockService;
import com.acme.sportplatform.registrations.*;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Every capacity decision runs after taking the event lock, before any discipline/registration lock. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RegistrationCapacityService {
    private static final Set<String> OCCUPYING = Set.of("SUBMITTED", "UNDER_REVIEW", "CONFIRMED", "PAID", "ADMITTED");
    private final RegistrationRepository registrations;
    private final EventLookup events;
    private final EventDisciplineLookup disciplines;
    private final EventLockService locks;
    private final ObjectMapper json;
    private final ApplicationEventPublisher publisher;

    public RegistrationCapacityService(RegistrationRepository registrations, EventLookup events,
            EventDisciplineLookup disciplines, EventLockService locks, ObjectMapper json,
            ApplicationEventPublisher publisher) {
        this.registrations = registrations; this.events = events; this.disciplines = disciplines;
        this.locks = locks; this.json = json; this.publisher = publisher;
    }

    public void lock(UUID eventId) { locks.lockWritableForUpdate(eventId); }

    public void allocate(RegistrationEntity registration, String availableStatus) {
        var discipline = disciplines.getForScheduling(registration.getEventDisciplineId());
        if (hasPlace(registration, discipline)) {
            registration.setStatus(availableStatus);
            registration.setWaitlistPosition(null);
        } else {
            int position = registrations.findByEventIdAndStatus(registration.getEventId(), "WAITLISTED").stream()
                    .mapToInt(RegistrationEntity::getWaitlistPosition).max().orElse(0) + 1;
            registration.setStatus("WAITLISTED");
            registration.setWaitlistPosition(position);
            publisher.publishEvent(new RegistrationWaitlistedEvent(registration.getId(), registration.getEventId(),
                    registration.getEventDisciplineId(), registration.getParticipantProfileId(), position));
        }
    }

    public void promote(UUID eventId) {
        if ("ARCHIVED".equals(events.getById(eventId).status())) return;
        registrations.flush();
        var waiting = registrations.findByEventIdAndStatus(eventId, "WAITLISTED").stream()
                .sorted(Comparator.comparing(RegistrationEntity::getWaitlistPosition)).toList();
        for (var registration : waiting) {
            var discipline = disciplines.getForScheduling(registration.getEventDisciplineId());
            if (!hasPlace(registration, discipline)) continue;
            Integer position = registration.getWaitlistPosition();
            registration.setStatus("SUBMITTED");
            registration.setWaitlistPosition(null);
            registration.setSubmittedAt(OffsetDateTime.now());
            registration.setUpdatedAt(registration.getSubmittedAt());
            registrations.saveAndFlush(registration);
            publisher.publishEvent(new RegistrationWaitlistPromotedEvent(registration.getId(), eventId,
                    registration.getEventDisciplineId(), registration.getParticipantProfileId(), position));
        }
    }

    public boolean hasPlace(RegistrationEntity candidate, EventDisciplineLookupResult discipline) {
        var eventLimit = events.lockById(candidate.getEventId()).maxParticipants();
        if (eventLimit == null && discipline.maxParticipants() == null && discipline.participantLimit() == null
                && (discipline.limitsByAgeGroup() == null || discipline.limitsByAgeGroup().isEmpty())) return true;
        var active = registrations.findByEventId(candidate.getEventId()).stream()
                .filter(r -> !r.getId().equals(candidate.getId()) && OCCUPYING.contains(r.getStatus())).toList();
        // A swimmer's second discipline does not consume another event participant place.
        var profiles = active.stream().map(RegistrationEntity::getParticipantProfileId).collect(java.util.stream.Collectors.toSet());
        if (eventLimit != null && !profiles.contains(candidate.getParticipantProfileId()) && profiles.size() >= eventLimit)
            return false;
        var entries = active.stream().filter(r -> r.getEventDisciplineId().equals(discipline.id())).toList();
        if (discipline.maxParticipants() != null && entries.size() >= discipline.maxParticipants()) return false;
        if (discipline.participantLimit() != null && entries.size() >= discipline.participantLimit()) return false;
        if (discipline.limitsByAgeGroup() != null && !discipline.limitsByAgeGroup().isEmpty()) {
            String group = ageGroup(candidate.getParticipantSnapshot(), discipline);
            Integer limit = group == null ? null : discipline.limitsByAgeGroup().get(group);
            if (limit != null && entries.stream().filter(r -> Objects.equals(group, ageGroup(r.getParticipantSnapshot(), discipline))).count() >= limit)
                return false;
        }
        return true;
    }

    public Map<String, Long> groupCounts(List<RegistrationEntity> active, EventDisciplineLookupResult discipline) {
        Map<String, Long> counts = new HashMap<>();
        for (var registration : active) {
            if (!registration.getEventDisciplineId().equals(discipline.id())) continue;
            String group = ageGroup(registration.getParticipantSnapshot(), discipline);
            if (group != null) counts.merge(group, 1L, Long::sum);
        }
        return counts;
    }

    private String ageGroup(String snapshot, EventDisciplineLookupResult discipline) {
        try {
            var profile = json.readTree(snapshot);
            String gender = profile.path("gender").asText();
            if ("MALE".equals(gender)) gender = "M";
            if ("FEMALE".equals(gender)) gender = "F";
            int birthYear = LocalDate.parse(profile.path("birthDate").asText()).getYear();
            final String athleteGender = gender;
            return discipline.ageGroups().stream().filter(g -> g.includes(athleteGender, birthYear))
                    .map(g -> g.code()).findFirst().orElse(null);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid participant snapshot for capacity calculation", exception);
        }
    }
}
