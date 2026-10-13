package com.acme.sportplatform.documents.application;

import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.documents.domain.Protocol;
import com.acme.sportplatform.documents.domain.Protocol.*;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.organizations.OrganizationLookup;
import com.acme.sportplatform.organizations.VenueLookup;
import com.acme.sportplatform.results.ResultsProtocolLookup;
import com.acme.sportplatform.results.ResultsProtocolLookup.Entry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DocumentDataService {
    private static final Set<String> PUBLIC_HEATS = Set.of("PUBLISHED", "IN_PROGRESS", "FINISHED", "COMPLETED");
    private static final Set<String> PUBLIC_RESULTS = Set.of("FINISHED", "COMPLETED");
    private final EventLookup events;
    private final EventDisciplineLookup disciplines;
    private final ResultsProtocolLookup results;
    private final OrganizationLookup organizations;
    private final VenueLookup venues;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DocumentDataService(EventLookup events, EventDisciplineLookup disciplines, ResultsProtocolLookup results,
            OrganizationLookup organizations, VenueLookup venues, ObjectMapper mapper,
            @Qualifier("documentsClock") Clock clock) {
        this.events = events;
        this.disciplines = disciplines;
        this.results = results;
        this.organizations = organizations;
        this.venues = venues;
        this.mapper = mapper;
        this.clock = clock;
    }

    public Protocol protocol(UUID eventId, boolean publishedOnly) {
        var event = events.protocolHeader(eventId);
        var venue = event.venueId() == null ? null : venues.protocolVenue(event.venueId());
        var zone = venue == null ? ZoneOffset.UTC : ZoneId.of(venue.timezone());
        int laneCount = laneCount(venue);
        var header = new Header(event.title(), venue == null ? "Площадка не указана" :
                venue.name() + (venue.city() == null ? "" : ", " + venue.city()),
                date(event.startsAt(), zone), organizations.nameById(event.organizationId()),
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss z", Locale.forLanguageTag("ru"))
                        .format(clock.instant().atZone(zone)));
        var sessions = events.getById(eventId).sessions();
        var sessionOrder = sessions.stream().collect(Collectors.toMap(s -> s.id(), s -> s.orderNo()));
        var sessionNames = sessions.stream().collect(Collectors.toMap(s -> s.id(), s -> s.name()));
        var program = new ArrayList<>(publishedOnly ? disciplines.findPublishedByEventId(eventId) : disciplines.findByEventId(eventId));
        program.sort(Comparator.comparing((EventDisciplineSummary d) -> sessionOrder.getOrDefault(d.sessionId(), Integer.MAX_VALUE))
                .thenComparing(EventDisciplineSummary::orderInSession, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EventDisciplineSummary::id));
        var heats = results.heats(program.stream().map(EventDisciplineSummary::id).toList()).stream()
                .filter(h -> !publishedOnly || PUBLIC_HEATS.contains(h.status()))
                .collect(Collectors.groupingBy(ResultsProtocolLookup.Heat::disciplineId));
        var views = new ArrayList<Discipline>();
        int number = 0;
        for (var discipline : program) {
            number++;
            var disciplineHeats = heats.getOrDefault(discipline.id(), List.of());
            if (disciplineHeats.isEmpty()) continue;
            var startHeats = disciplineHeats.stream().sorted(Comparator.comparingInt(ResultsProtocolLookup.Heat::number))
                    .map(h -> new Heat(h.number(), groupName(discipline, h.ageGroupCode()), time(h.scheduledAt(), zone),
                            startRows(h.entries(), laneCount))).toList();
            var outcomes = disciplineHeats.stream().filter(h -> !publishedOnly || PUBLIC_RESULTS.contains(h.status()))
                    .flatMap(h -> h.entries().stream().filter(e -> e.outcome() != null && !"PENDING".equals(e.outcome().status()))
                            .map(e -> new ResultEntry(e, h.ageGroupCode()))).toList();
            var sections = new ArrayList<ResultSection>();
            if (!outcomes.isEmpty()) sections.add(section("Абсолют", outcomes, false));
            for (var group : discipline.ageGroups()) {
                var grouped = outcomes.stream().filter(e -> inGroup(e, discipline, group)).toList();
                if (!grouped.isEmpty()) sections.add(section(group.name(), grouped, true));
            }
            views.add(new Discipline(number, discipline.name(), sessionNames.getOrDefault(discipline.sessionId(), "Без сессии"),
                    startHeats, List.copyOf(sections)));
        }
        return new Protocol(header, List.copyOf(views));
    }

    private int laneCount(VenueLookup.ProtocolVenue venue) {
        if (venue == null) return 8;
        try { return mapper.readTree(venue.metadata() == null ? "{}" : venue.metadata()).path("lanes").asInt(8); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new com.acme.sportplatform.common.exception.BusinessException("documents.invalid_data", "Некорректные параметры площадки");
        }
    }

    private List<StartRow> startRows(List<Entry> entries, int laneCount) {
        var byLane = entries.stream().filter(e -> e.lane() != null).collect(Collectors.toMap(Entry::lane, e -> e));
        var rows = new ArrayList<StartRow>();
        for (int lane = 1; lane <= laneCount; lane++) {
            var e = byLane.get(lane);
            rows.add(e == null ? new StartRow(lane, "—", "—", "—", "—", false) :
                    new StartRow(lane, athlete(e), year(e), club(e), e.withdrawn() ? "—" : formatTime(e.seedTimeMs(), "NT"), e.withdrawn()));
        }
        return List.copyOf(rows);
    }

    private record ResultEntry(Entry entry, String ageGroup) {}

    private boolean inGroup(ResultEntry row, EventDisciplineSummary discipline, AgeGroup group) {
        if ("BY_AGE_GROUP".equals(discipline.seedingMode()) && row.ageGroup() != null)
            return group.code().equals(row.ageGroup());
        var entry = row.entry();
        String gender = switch (entry.gender() == null ? "" : entry.gender()) {
            case "MALE", "М" -> "M";
            case "FEMALE", "Ж" -> "F";
            default -> entry.gender();
        };
        return entry.birthYear() != null && group.includes(gender, entry.birthYear());
    }

    private ResultSection section(String name, List<ResultEntry> entries, boolean grouped) {
        var ordered = entries.stream().sorted(Comparator
                .comparing((ResultEntry r) -> ranked(r.entry()) ? (grouped ? r.entry().outcome().placeInAgeGroup() : r.entry().outcome().place()) : null,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.entry().outcome().timeMs(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(r -> r.entry().lastName()).thenComparing(r -> r.entry().firstName())).toList();
        var placed = new ArrayList<ResultRow>();
        var others = new ArrayList<ResultRow>();
        for (var r : ordered) {
            var e = r.entry();
            var outcome = e.outcome();
            String status = status(e);
            var row = new ResultRow(ranked(e) ? (grouped ? outcome.placeInAgeGroup() : outcome.place()) : null,
                    athlete(e), year(e), club(e), formatTime(outcome.timeMs(), "—"), status, "CORRECTED".equals(outcome.status()));
            (ranked(e) ? placed : others).add(row);
        }
        return new ResultSection(name, List.copyOf(placed), List.copyOf(others));
    }

    private static String status(Entry e) {
        var r = e.outcome();
        String value = "CORRECTED".equals(r.status()) && r.timeMs() == null ? r.rawValue() : r.status();
        if (value == null) return "CORRECTED";
        return switch (value) {
            case "DID_NOT_START" -> "DNS";
            case "DID_NOT_FINISH" -> "DNF";
            case "DISQUALIFIED" -> "DSQ";
            case "CORRECTED" -> "VALID";
            default -> value;
        };
    }

    private static boolean ranked(Entry e) { return "VALID".equals(status(e)); }
    private static String athlete(Entry e) { return e.lastName() + " " + e.firstName(); }
    private static String year(Entry e) { return e.birthYear() == null ? "—" : e.birthYear().toString(); }
    private static String club(Entry e) { return e.club() == null ? "—" : e.club(); }
    private static String groupName(EventDisciplineSummary d, String code) {
        return code == null ? "" : d.ageGroups().stream().filter(g -> code.equals(g.code())).map(AgeGroup::name).findFirst().orElse(code);
    }
    private static String date(OffsetDateTime value, ZoneId zone) {
        return value == null ? "—" : DateTimeFormatter.ofPattern("dd.MM.yyyy").format(value.atZoneSameInstant(zone));
    }
    private static String time(OffsetDateTime value, ZoneId zone) {
        return value == null ? "—" : DateTimeFormatter.ofPattern("HH:mm:ss").format(value.atZoneSameInstant(zone));
    }
    private static String formatTime(Long ms, String missing) {
        return ms == null ? missing : String.format(Locale.ROOT, "%02d:%02d.%02d", ms / 60000, ms / 1000 % 60, ms / 10 % 100);
    }
}
