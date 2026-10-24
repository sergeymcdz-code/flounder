package com.acme.sportplatform.publication.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.publication.api.PublicResponses.*;
import com.acme.sportplatform.publication.domain.ReadModels;
import com.acme.sportplatform.publication.infrastructure.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class PublicPublicationService {
    private final com.acme.sportplatform.events.EventPublicLookup events;
    private final com.acme.sportplatform.competition.EventDisciplineLookup eventDisciplines;
    private final PublicationReadRepository reads;
    private final com.acme.sportplatform.results.ResultsPublicationLookup publications;
    private final com.acme.sportplatform.organizations.OrganizationLookup organizations;
    private final ObjectMapper mapper;
    private final Clock clock;
    public PublicPublicationService(PublicationReadRepository reads, ObjectMapper mapper,
            @Qualifier("publicationClock") Clock clock, com.acme.sportplatform.events.EventPublicLookup events, com.acme.sportplatform.competition.EventDisciplineLookup eventDisciplines, com.acme.sportplatform.organizations.OrganizationLookup organizations,
            com.acme.sportplatform.results.ResultsPublicationLookup publications) {
        this.publications = publications;
        this.organizations = organizations;
        this.events = events; this.eventDisciplines = eventDisciplines;

        this.reads = reads;
        this.mapper = mapper; this.clock = clock;
    }

    @Cacheable(cacheNames = "publicEvent", cacheManager = "publicationCacheManager")
    public Event event(String slug) {
        var event = requireEvent(slug);
        var disciplines = reads.disciplines(event.id());
        var capacity = reads.capacity(event.id());
        var fees = eventDisciplines.findPublishedByEventId(event.id()).stream().collect(Collectors.toMap(com.acme.sportplatform.competition.EventDisciplineSummary::id,Function.identity()));
        Set<UUID> published = reads.units(event.id()).stream().map(ReadModels.Unit::disciplineId).collect(Collectors.toSet());
        JsonNode meta = json(event.venueMeta());
        Venue venue = event.venueName() == null ? null : new Venue(event.venueName(), event.venueCity(),
                meta.path("lanes").asInt(8), nullableInt(meta,"courseLength"), event.venueTimezone());
        var organization = event.organizationId() == null ? null : organizations.cards(List.of(event.organizationId())).get(event.organizationId());
        return new Event(event.id(), event.sportId(), event.title(), event.description(), event.registrationOpenAt(),
                event.registrationCloseAt(), event.eventStartAt(), event.eventEndAt(), event.status(), event.slug(),
                registrationOpen(event), venue, sessionViews(event.id()),
                disciplines.stream().map(d -> new Discipline(d.id(), d.number(), d.name(), d.distanceM(), d.stroke(),
                        d.gender(), d.sessionId(), d.orderInSession(), published.contains(d.id()), fees.containsKey(d.id()) ? fees.get(d.id()).entryFeeAmount() : null, fees.containsKey(d.id()) ? fees.get(d.id()).entryFeeCurrency() : null)).toList(), capacity.maxParticipants(), capacity.participantsCount(), organization == null ? null : new Organization(organization.name(), organization.publicSlug(), organization.hasLogo()), events.getPublicBySlug(slug).payment());
    }

    @Cacheable(cacheNames = "publicStartList", cacheManager = "publicationCacheManager")
    public StartList startList(String slug) {
        var event = requireEvent(slug);
        var disciplines = reads.disciplines(event.id());
        var units = reads.units(event.id());
        var entries = reads.entries(event.id()).stream().collect(Collectors.groupingBy(ReadModels.Entry::unitId));
        int laneCount = json(event.venueMeta()).path("lanes").asInt(8);
        Map<UUID,List<ReadModels.Unit>> unitsByDiscipline = units.stream().collect(Collectors.groupingBy(ReadModels.Unit::disciplineId));
        List<StartSession> response = new ArrayList<>();
        for (Session session : sessionViews(event.id())) {
            var views = startDisciplines(disciplines.stream().filter(d -> session.id().equals(d.sessionId())).toList(),
                    unitsByDiscipline, entries, laneCount);
            if (!views.isEmpty()) response.add(new StartSession(session.id().toString(),session.name(),session.startsAt(),views));
        }
        var unassigned = startDisciplines(disciplines.stream().filter(d -> d.sessionId() == null).toList(),
                unitsByDiscipline, entries, laneCount);
        if (!unassigned.isEmpty()) response.add(new StartSession("unassigned","Без сессии",null,unassigned));
        return new StartList(List.copyOf(response));
    }
    private List<StartDiscipline> startDisciplines(List<ReadModels.Discipline> disciplines,
            Map<UUID,List<ReadModels.Unit>> units, Map<UUID,List<ReadModels.Entry>> entries, int laneCount) {
        return disciplines.stream().filter(d -> units.containsKey(d.id())).map(d -> new StartDiscipline(d.id(),d.number(),d.name(),
                units.get(d.id()).stream().sorted(Comparator.comparingInt(ReadModels.Unit::number))
                    .map(u -> new Heat(u.id(),u.number(),u.ageGroupCode(),u.scheduledAt(),u.sequenceInSession(),u.status(),
                            lanes(entries.getOrDefault(u.id(),List.of()),laneCount))).toList())).toList();
    }
    private List<Lane> lanes(List<ReadModels.Entry> entries, int laneCount) {
        Map<Integer,ReadModels.Entry> byLane = entries.stream().filter(e -> e.lane() != null)
                .collect(Collectors.toMap(ReadModels.Entry::lane,Function.identity()));
        List<Lane> lanes = new ArrayList<>();
        for (int lane = 1; lane <= laneCount; lane++) {
            var entry = byLane.get(lane);
            lanes.add(entry == null ? new EmptyLane(lane,true)
                    : new OccupiedLane(lane,entry.withdrawn() ? true : null,entry.registrationId(),
                        new Athlete(entry.lastName(),entry.firstName(),entry.birthYear(),entry.club()),
                        entry.seedTimeMs(),display(entry.seedTimeMs(),null)));
        }
        return List.copyOf(lanes);
    }

    @Cacheable(cacheNames = "publicSchedule", cacheManager = "publicationCacheManager")
    public List<Schedule> schedule(String slug) {
        var event = requireEvent(slug);
        var disciplines = disciplineMap(event.id());
        Map<UUID,String> names = sessionViews(event.id()).stream().collect(Collectors.toMap(Session::id,Session::name));
        return reads.units(event.id()).stream().map(u -> {
            var discipline = disciplines.get(u.disciplineId());
            return new Schedule(names.get(discipline.sessionId()),discipline.name(),u.number(),u.sequenceInSession(),u.scheduledAt(),u.status(),u.scheduleShiftedAt());
        }).toList();
    }

    @Cacheable(cacheNames = "publicLive", cacheManager = "publicationCacheManager")
    public Live live(String slug) {
        var event = requireEvent(slug);
        var units = reads.units(event.id());
        var state = reads.liveState(event.id());
        var now = OffsetDateTime.now(clock);
        var current = state.isPresent() ? units.stream().filter(u -> u.id().equals(state.get().currentUnitId())).findFirst().orElse(null)
                : units.stream().filter(u -> upcoming(u,now)).findFirst().orElse(null);
        var disciplines = disciplineMap(event.id());
        var entries = reads.entries(event.id()).stream().collect(Collectors.groupingBy(ReadModels.Entry::unitId));
        int laneCount = json(event.venueMeta()).path("lanes").asInt(8);
        long delaySec = current == null || current.actualStartedAt() == null || current.scheduledAt() == null
                ? 0 : Math.max(0, java.time.Duration.between(current.scheduledAt(), current.actualStartedAt()).getSeconds());
        var candidates = current == null ? units.stream().filter(u -> upcoming(u,now))
                : units.subList(units.indexOf(current) + 1,units.size()).stream().filter(PublicPublicationService::canRun);
        var next = candidates.limit(3)
                .map(u -> liveUnit(u,disciplines.get(u.disciplineId()),entries.getOrDefault(u.id(),List.of()),laneCount,delaySec)).toList();
        return new Live(current == null ? null : liveUnit(current,disciplines.get(current.disciplineId()),
                entries.getOrDefault(current.id(),List.of()),laneCount,delaySec),next,
                state.map(ReadModels.LiveState::updatedAt).orElse(now),delaySec);
    }
    private boolean upcoming(ReadModels.Unit unit, OffsetDateTime now) {
        return "PUBLISHED".equals(unit.status()) && unit.scheduledAt() != null && !unit.scheduledAt().isBefore(now);
    }
    public static boolean canRun(ReadModels.Unit unit) {
        return "PUBLISHED".equals(unit.status()) || "IN_PROGRESS".equals(unit.status());
    }
    private LiveUnit liveUnit(ReadModels.Unit unit, ReadModels.Discipline discipline,
            List<ReadModels.Entry> entries, int laneCount, long delaySec) {
        return new LiveUnit(unit.id(),unit.number(),unit.ageGroupCode(),unit.scheduledAt(),
                unit.sequenceInSession(),unit.status(),lanes(entries,laneCount),
                discipline.id(),discipline.number(),discipline.name(),unit.actualStartedAt(),
                unit.scheduledAt() == null ? null : unit.scheduledAt().plusSeconds(delaySec));
    }

    @Cacheable(cacheNames = "publicResults", cacheManager = "publicationCacheManager")
    public Results results(String slug) {
        var event = requireEvent(slug);
        var disciplines = reads.disciplines(event.id());
        var units = reads.units(event.id()).stream().collect(Collectors.toMap(ReadModels.Unit::id,Function.identity()));
        var byDiscipline = reads.entries(event.id()).stream().filter(e -> e.resultStatus() != null)
                .collect(Collectors.groupingBy(e -> units.get(e.unitId()).disciplineId()));
        var official = publications.officialByDiscipline(disciplines.stream().map(ReadModels.Discipline::id).toList());
        List<DisciplineResults> result = new ArrayList<>();
        for (var d : disciplines) {
            var rows = byDiscipline.getOrDefault(d.id(),List.of());
            if (rows.isEmpty()) continue;
            List<AgeGroupResults> groups = new ArrayList<>();
            for (JsonNode group : json(d.ageGroups())) {
                var groupRows = rows.stream().filter(e -> inAgeGroup(e,units.get(e.unitId()),d,group)).toList();
                if (!groupRows.isEmpty()) groups.add(new AgeGroupResults(group.path("code").asText(),group.path("name").asText(),resultRows(groupRows,true)));
            }
            result.add(new DisciplineResults(d.id(),d.number(),d.name(),resultRows(rows,false),List.copyOf(groups), official.getOrDefault(d.id(), false)));
        }
        return new Results(List.copyOf(result), !result.isEmpty() && !official.isEmpty()
                && official.values().stream().allMatch(Boolean::booleanValue));
    }
    private boolean inAgeGroup(ReadModels.Entry entry, ReadModels.Unit unit, ReadModels.Discipline discipline, JsonNode group) {
        if ("BY_AGE_GROUP".equals(discipline.seedingMode()) && unit.ageGroupCode() != null)
            return unit.ageGroupCode().equals(group.path("code").asText());
        String gender = switch (entry.gender() == null ? "" : entry.gender()) {
            case "MALE", "М" -> "M"; case "FEMALE", "Ж" -> "F"; default -> entry.gender();
        };
        String expected = group.path("gender").asText("X");
        return ("X".equals(expected) || expected.equals(gender))
                && (group.path("birthYearFrom").isNull() || !group.has("birthYearFrom") || entry.birthYear() >= group.path("birthYearFrom").asInt())
                && (group.path("birthYearTo").isNull() || !group.has("birthYearTo") || entry.birthYear() <= group.path("birthYearTo").asInt());
    }
    private List<ResultRow> resultRows(List<ReadModels.Entry> entries, boolean ageGroup) {
        return entries.stream().map(e -> new ResultRow(hasPlace(e.resultStatus()) ? (ageGroup ? e.placeInAgeGroup() : e.place()) : null,
                e.lastName(),e.firstName(),e.birthYear(),e.club(),display(e.timeMs(),e.resultStatus()),e.timeMs(),publicStatus(e.resultStatus())))
                .sorted(Comparator.comparing((ResultRow row) -> !hasPlace(row.status()))
                        .thenComparing(ResultRow::place,Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ResultRow::timeMs,Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ResultRow::lastName).thenComparing(ResultRow::firstName)).toList();
    }

    @Cacheable(cacheNames = "publicAthletes", cacheManager = "publicationCacheManager")
    public List<AthleteSwims> athletes(String slug, String query) {
        var event = requireEvent(slug);
        String prefix = query == null ? "" : query.strip().toLowerCase(Locale.ROOT).replace('ё','е');
        if (prefix.isEmpty()) return List.of();
        if (prefix.length() > 128) throw new BusinessException("publication.invalid_query","Фамилия для поиска не должна превышать 128 символов.");
        var units = reads.units(event.id()).stream().collect(Collectors.toMap(ReadModels.Unit::id,Function.identity()));
        var disciplines = disciplineMap(event.id());
        Map<UUID,List<ReadModels.Entry>> grouped = reads.entries(event.id(),prefix).stream()
                .collect(Collectors.groupingBy(ReadModels.Entry::profileId,LinkedHashMap::new,Collectors.toList()));
        return grouped.entrySet().stream().map(group -> {
            var athlete = group.getValue().getFirst();
            var swims = group.getValue().stream().map(e -> {
                var unit = units.get(e.unitId());
                return new Swim(disciplines.get(unit.disciplineId()).name(),unit.number(),e.lane(),unit.scheduledAt(),
                        e.resultStatus() == null ? null : new SwimResult(display(e.timeMs(),e.resultStatus()),publicStatus(e.resultStatus()),
                                hasPlace(e.resultStatus()) ? e.place() : null,hasPlace(e.resultStatus()) ? e.placeInAgeGroup() : null));
            }).toList();
            return new AthleteSwims(group.getKey(),athlete.lastName(),athlete.firstName(),athlete.birthYear(),athlete.club(),swims);
        }).sorted(Comparator.comparing(AthleteSwims::lastName).thenComparing(AthleteSwims::firstName).thenComparing(AthleteSwims::athleteKey)).toList();
    }
    private boolean registrationOpen(ReadModels.Event event) {
        var now = OffsetDateTime.now(clock);
        return "REGISTRATION_OPEN".equals(event.status())
                && (event.registrationOpenAt() == null || !now.isBefore(event.registrationOpenAt()))
                && (event.registrationCloseAt() == null || !now.isAfter(event.registrationCloseAt()));
    }
    private ReadModels.Event requireEvent(String slug) {
        return reads.event(slug).orElseThrow(() -> new BusinessException("events.public_not_found","Публичное событие не найдено."));
    }
    private Map<UUID,ReadModels.Discipline> disciplineMap(UUID eventId) {
        return reads.disciplines(eventId).stream().collect(Collectors.toMap(ReadModels.Discipline::id,Function.identity()));
    }
    private List<Session> sessionViews(UUID eventId) {
        return reads.sessions(eventId).stream().map(s -> new Session(s.id(),s.name(),s.orderNo(),
                s.startsAt(),s.breakBetweenEventsSec())).toList();
    }
    private JsonNode json(String value) {
        try { return mapper.readTree(value == null ? "{}" : value); }
        catch (JsonProcessingException ex) { throw new BusinessException("publication.invalid_data","Некорректные данные публичного протокола."); }
    }
    private static Integer nullableInt(JsonNode node, String key) { return node.hasNonNull(key) ? node.path(key).asInt() : null; }
    public static String publicStatus(String status) {
        if (status == null) return null;
        return switch (status) {
            case "DID_NOT_START" -> "DNS"; case "DID_NOT_FINISH" -> "DNF"; case "DISQUALIFIED" -> "DSQ"; case "CORRECTED", "EXHIBITION" -> "VALID"; default -> status;
        };
    }
    private static boolean hasPlace(String status) { return "VALID".equals(status) || "CORRECTED".equals(status); }
    public static String display(Long timeMs, String status) {
        String code = publicStatus(status);
        if ("DNS".equals(code) || "DNF".equals(code) || "DSQ".equals(code)) return code;
        return timeMs == null ? null : String.format(Locale.ROOT,"%02d:%02d.%02d",timeMs / 60000,(timeMs / 1000) % 60,(timeMs / 10) % 100);
    }
}
