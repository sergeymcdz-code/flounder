package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.VenueLookup;
import com.acme.sportplatform.registrations.*;
import com.acme.sportplatform.results.CompetitionUnitStore;
import com.acme.sportplatform.scheduling.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
class SchedulingData {
    final CompetitionUnitStore units;
    final EventAccessService access;
    final EventLookup events;
    final EventLockService locks;
    private final EventDisciplineLookup disciplines;
    private final RegistrationLookup registrations;
    private final EventSessionLookup sessions;
    private final VenueLookup venues;
    private final ObjectMapper json;

    SchedulingData(
            CompetitionUnitStore units,
            EventAccessService access,
            EventLookup events,
            EventLockService locks,
            EventDisciplineLookup disciplines,
            RegistrationLookup registrations,
            EventSessionLookup sessions,
            VenueLookup venues,
            ObjectMapper json) {
        this.units = units;
        this.access = access;
        this.events = events;
        this.locks = locks;
        this.disciplines = disciplines;
        this.registrations = registrations;
        this.sessions = sessions;
        this.venues = venues;
        this.json = json;
    }

    EventDisciplineLookupResult discipline(UUID id) {
        return disciplines.getForScheduling(id);
    }

    List<EventDisciplineLookupResult> disciplines(UUID eventId) {
        Map<UUID, Integer> order = new HashMap<>();
        sessions.findByEventId(eventId).forEach(s -> order.put(s.id(), s.orderNo()));
        return disciplines.findForSchedulingByEventId(eventId).stream()
                .sorted(
                        Comparator.comparingInt(
                                        (EventDisciplineLookupResult d) ->
                                                order.getOrDefault(
                                                        d.sessionId(), Integer.MAX_VALUE))
                                .thenComparingInt(
                                        d ->
                                                d.orderInSession() == null
                                                        ? Integer.MAX_VALUE
                                                        : d.orderInSession())
                                .thenComparing(EventDisciplineLookupResult::code)
                                .thenComparing(d -> d.id().toString()))
                .toList();
    }

    static boolean isConfigured(EventDisciplineLookupResult d) {
        return d.distanceM() != null && d.gender() != null && d.stroke() != null;
    }

    DisciplineSpec spec(EventDisciplineLookupResult d) {
        if (!isConfigured(d))
            throw new BusinessException(
                    "scheduling.discipline_not_configured",
                    "Укажите дистанцию, стиль и пол дисциплины перед посевом");
        List<AgeGroupSpec> groups = new ArrayList<>();
        JsonNode ageGroups = json.valueToTree(d.ageGroups());
        if (!ageGroups.isArray())
            throw new BusinessException(
                    "scheduling.invalid_age_groups", "Возрастные группы должны быть массивом");
        Set<String> groupCodes = new HashSet<>();
        for (JsonNode g : ageGroups) {
            var group =
                    new AgeGroupSpec(
                            text(g, "code"),
                            text(g, "name"),
                            text(g, "gender"),
                            integer(g, "birthYearFrom"),
                            integer(g, "birthYearTo"));
            if (group.code() == null
                    || group.code().isBlank()
                    || group.gender() == null
                    || !Set.of("M", "F", "X").contains(group.gender())
                    || !groupCodes.add(group.code())
                    || (group.birthYearFrom() != null
                            && group.birthYearTo() != null
                            && group.birthYearFrom() > group.birthYearTo()))
                throw new BusinessException(
                        "scheduling.invalid_age_groups",
                        "Некорректные коды, пол или границы возрастных групп");
            groups.add(group);
        }
        int number = d.orderInSession() == null ? 1 : d.orderInSession();
        JsonNode settings = tree(d.settingsJson());
        if (settings.hasNonNull("number")) number = settings.get("number").asInt();
        return new DisciplineSpec(
                d.id(),
                number,
                d.distanceM(),
                d.stroke(),
                d.gender(),
                groups,
                d.seedingMode(),
                d.sessionId(),
                d.orderInSession() == null ? number : d.orderInSession(),
                d.heatDurationSec());
    }

    PoolSpec pool(UUID eventId) {
        JsonNode meta = tree(venues.getMetadata(events.getById(eventId).venueId()));
        int lanes = meta.path("lanes").asInt(8), min = meta.path("minSwimmersInHeat").asInt(3);
        if (lanes < 1 || min < 1 || min > lanes)
            throw new BusinessException(
                    "scheduling.invalid_pool", "Некорректные настройки дорожек бассейна");
        return new PoolSpec(lanes, min);
    }

    SchedulingRules rules(UUID eventId) {
        SchedulingRules defaults = SchedulingRules.defaults();
        JsonNode settings = tree(events.getById(eventId).settingsJson());
        JsonNode rules =
                settings.has("schedulingRules") ? settings.get("schedulingRules") : settings;
        return new SchedulingRules(
                defaults.heatDurationSecByDistance(),
                defaults.defaultHeatDurationSec(),
                defaults.breakBetweenEventsSec(),
                rules.path("minRestBetweenStartsSec").asInt(defaults.minRestBetweenStartsSec()));
    }

    List<SessionSpec> sessions(UUID eventId) {
        return sessions.findByEventId(eventId).stream()
                .map(
                        s ->
                                new SessionSpec(
                                        s.id(),
                                        s.orderNo(),
                                        s.startsAt(),
                                        s.breakBetweenEventsSec()))
                .toList();
    }

    List<SeedEntry> entries(UUID disciplineId) {
        return registrations.findByDisciplineId(disciplineId).stream()
                .filter(r -> Set.of("CONFIRMED", "PAID", "ADMITTED").contains(r.status()))
                .map(this::entry)
                .toList();
    }

    private SeedEntry entry(RegistrationLookupResult r) {
        JsonNode s = tree(r.participantSnapshot());
        Integer year = integer(s, "birthYear");
        if (year == null && s.hasNonNull("birthDate")) {
            try {
                year = LocalDate.parse(s.get("birthDate").asText()).getYear();
            } catch (java.time.DateTimeException ex) {
                year = null;
            }
        }
        return new SeedEntry(
                r.id(),
                r.participantProfileId(),
                text(s, "lastName"),
                text(s, "firstName"),
                year,
                text(s, "gender"),
                s.hasNonNull("club")
                        ? text(s, "club")
                        : s.hasNonNull("clubName") ? text(s, "clubName") : text(s, "city"),
                r.seedTimeMs());
    }

    List<SeededHeat> heats(List<CompetitionUnitStore.Unit> units, List<SeedEntry> entries) {
        Map<UUID, SeedEntry> byId = new HashMap<>();
        entries.forEach(e -> byId.put(e.registrationId(), e));
        return units.stream()
                .map(
                        u ->
                                new SeededHeat(
                                        u.number(),
                                        u.ageGroupCode(),
                                        u.entries().stream()
                                                .filter(e -> !"WITHDRAWN".equals(e.status()))
                                                .map(
                                                        e ->
                                                                new SeededLane(
                                                                        e.lane(),
                                                                        e.registrationId(),
                                                                        byId.containsKey(
                                                                                        e
                                                                                                .registrationId())
                                                                                ? byId.get(
                                                                                                e
                                                                                                        .registrationId())
                                                                                        .athleteId()
                                                                                : null,
                                                                        e.seedTimeMs()))
                                                .toList()))
                .toList();
    }

    List<ScheduledHeat> schedule(UUID eventId) {
        List<ScheduledHeat> out = new ArrayList<>();
        for (var discipline : disciplines(eventId)) {
            var entries = entries(discipline.id());
            var units = this.units.findByDisciplineId(discipline.id());
            var heats = heats(units, entries);
            for (int i = 0; i < units.size(); i++)
                out.add(
                        new ScheduledHeat(
                                discipline.id(),
                                units.get(i).number(),
                                units.get(i).scheduledAt(),
                                0,
                                heats.get(i).lanes().stream()
                                        .map(SeededLane::athleteId)
                                        .filter(Objects::nonNull)
                                        .toList()));
        }
        return out;
    }

    static UUID userId(AuthenticatedUserPrincipal principal) {
        return principal == null ? null : principal.getUserId();
    }

    static List<Violation> publishedWithdrawalViolations(CompetitionUnitStore.Unit unit) {
        if (!"PUBLISHED".equals(unit.status())) return List.of();
        return unit.entries().stream()
                .filter(entry -> "WITHDRAWN".equals(entry.status()))
                .map(entry -> new Violation("PUBLISHED_HEAT_CHANGED", Violation.Severity.WARNING,
                        "Опубликованный заплыв изменён: участник снят, дорожка " + entry.lane(),
                        unit.disciplineId(), unit.number(), null, entry.registrationId()))
                .toList();
    }

    private JsonNode tree(String value) {
        try {
            return json.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(
                    "scheduling.invalid_snapshot", "Некорректные данные дисциплины или участника");
        }
    }

    private static String text(JsonNode node, String name) {
        return node.hasNonNull(name) ? node.get(name).asText() : null;
    }

    private static Integer integer(JsonNode node, String name) {
        return node.hasNonNull(name) ? node.get(name).asInt() : null;
    }
}
