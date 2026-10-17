package com.acme.sportplatform.documents;

import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.documents.application.DocumentDataService;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.organizations.*;
import com.acme.sportplatform.results.ResultsProtocolLookup;
import com.acme.sportplatform.results.ResultsProtocolLookup.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentDataServiceTest {
    final EventLookup events = mock(EventLookup.class);
    final EventDisciplineLookup disciplines = mock(EventDisciplineLookup.class);
    final ResultsProtocolLookup results = mock(ResultsProtocolLookup.class);
    final OrganizationLookup organizations = mock(OrganizationLookup.class);
    final VenueLookup venues = mock(VenueLookup.class);
    final UUID event = UUID.randomUUID(), discipline = UUID.randomUUID(), venue = UUID.randomUUID(), org = UUID.randomUUID();
    final OffsetDateTime start = OffsetDateTime.parse("2026-10-17T06:00:00Z");
    DocumentDataService service;
    int nextLane;

    @BeforeEach
    void fixture() {
        when(events.protocolHeader(event)).thenReturn(new EventProtocolHeader(event, "Кубок", org, venue, start, start.plusHours(6), "cup"));
        when(events.getById(event)).thenReturn(new EventLookupResult(event, UUID.randomUUID(), "PUBLISHED", null, null, List.of()));
        when(organizations.nameById(org)).thenReturn("Федерация");
        when(venues.protocolVenue(venue)).thenReturn(new VenueLookup.ProtocolVenue("Бассейн", "Самара", "Europe/Samara", "{\"lanes\":8}"));
        var program = List.of(program("BY_TIME_ALL"));
        when(disciplines.findByEventId(event)).thenReturn(program);
        when(disciplines.findPublishedByEventId(event)).thenReturn(program);
        service = new DocumentDataService(events, disciplines, results, organizations, venues,
                new ObjectMapper(), Clock.fixed(start.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void standaloneHeaderUsesTheVenueDateAndDoesNotLoadResults() {
        var lateStart = OffsetDateTime.parse("2026-10-17T21:00:00Z");
        when(events.protocolHeader(event)).thenReturn(new EventProtocolHeader(event, "Кубок", org, venue,
                lateStart, lateStart.plusHours(6), "cup"));
        var header = service.header(event);
        assertThat(header.date()).isEqualTo("18.10.2026");
        assertThat(header.venue()).isEqualTo("Бассейн, Самара");
        assertThat(header.organization()).isEqualTo("Федерация");
        verifyNoInteractions(results, disciplines);
    }

    @Test
    void publicStartListAndResultsRespectEveryHeatStatusAndDoNotLeakDrafts() {
        var heats = new ArrayList<Heat>();
        int number = 0;
        for (var status : List.of("DRAFT", "SCHEDULED", "PUBLISHED", "IN_PROGRESS", "FINISHED", "COMPLETED"))
            heats.add(new Heat(discipline, ++number, null, status, start,
                    List.of(entry(status, 2014, 1, "VALID", 32150L, null))));
        when(results.heats(List.of(discipline))).thenReturn(heats);
        var publicProtocol = service.protocol(event, true);
        assertThat(publicProtocol.disciplines().getFirst().heats()).extracting(h -> h.number()).containsExactly(3, 4, 5, 6);
        assertThat(publicProtocol.disciplines().getFirst().resultSections().getFirst().ranked())
                .extracting(r -> r.athlete()).containsExactlyInAnyOrder("FINISHED Иван", "COMPLETED Иван");
        assertThat(service.protocol(event, false).disciplines().getFirst().heats()).hasSize(6);
        assertThat(service.protocol(event, false).disciplines().getFirst().resultSections().getFirst().ranked()).hasSize(6);
        assertThat(publicProtocol.header().generatedAt()).contains("10:00:00");
        assertThat(publicProtocol.disciplines().getFirst().heats().getFirst().startsAt()).isEqualTo("10:00:00");
        assertThat(publicProtocol.disciplines().getFirst().heats().getFirst().rows()).hasSize(8);
    }

    @Test
    void preservesStoredTiePlacesAndGroupsAndMarksCorrectedTerminalOutcomes() {
        var entries = List.of(entry("Иванов", 2014, 1, "VALID", 32150L, null),
                entry("Петров", 2014, 1, "CORRECTED", 32150L, null),
                entry("Сидоров", 2012, 3, "VALID", 33000L, null),
                entry("Неявка", 2014, 99, "CORRECTED", null, "DID_NOT_START"),
                entry("Сход", 2014, 99, "DID_NOT_FINISH", null, null),
                entry("Дисквалификация", 2014, 99, "DISQUALIFIED", null, null),
                entry("Ожидающий", 2014, null, "PENDING", null, null));
        when(results.heats(List.of(discipline))).thenReturn(List.of(new Heat(discipline, 1, null, "FINISHED", start, entries)));
        var protocol = service.protocol(event, true).disciplines().getFirst();
        var overall = protocol.resultSections().getFirst();
        assertThat(overall.ranked()).extracting(r -> r.place()).containsExactly(1, 1, 3);
        assertThat(overall.ranked().get(1).corrected()).isTrue();
        assertThat(overall.unranked()).extracting(r -> r.status()).containsExactlyInAnyOrder("DNS", "DNF", "DSQ");
        assertThat(overall.unranked()).allMatch(r -> r.place() == null);
        assertThat(overall.unranked()).filteredOn(r -> r.status().equals("DNS")).allMatch(r -> r.corrected());
        assertThat(protocol.resultSections().get(1).ranked()).extracting(r -> r.athlete()).containsExactly("Иванов Иван", "Петров Иван");
        assertThat(protocol.resultSections().get(2).ranked()).extracting(r -> r.athlete()).containsExactly("Сидоров Иван");
    }

    @Test
    void byAgeGroupUsesHeatGroupAndWithdrawnAthletesKeepTheirLane() {
        when(disciplines.findPublishedByEventId(event)).thenReturn(List.of(program("BY_AGE_GROUP")));
        var e = entry("Снят", 2012, 1, "VALID", 32150L, null);
        var withdrawn = new Entry(4, e.lastName(), e.firstName(), e.birthYear(), e.gender(), e.club(), null, true, e.outcome());
        when(results.heats(List.of(discipline))).thenReturn(List.of(new Heat(discipline, 1, "young", "FINISHED", start, List.of(withdrawn))));
        var protocol = service.protocol(event, true).disciplines().getFirst();
        assertThat(protocol.resultSections()).extracting(s -> s.name()).containsExactly("Абсолют", "2014–2015");
        var lane = protocol.heats().getFirst().rows().get(3);
        assertThat(lane.withdrawn()).isTrue();
        assertThat(lane.athlete()).isEqualTo("Снят Иван");
        assertThat(lane.seed()).isEqualTo("—");
    }

    private Entry entry(String name, int year, Integer place, String status, Long ms, String raw) {
        return new Entry(++nextLane, name, "Иван", year, "MALE", "Волна", ms, false, new Outcome(ms, status, raw, place, place));
    }

    @Test
    void disciplineNumbersFollowPublicApiSessionOrderPositionCreationAndUnsignedUuidOrder() {
        var morning = UUID.randomUUID();
        var evening = UUID.randomUUID();
        var last = UUID.randomUUID();
        var sessions = List.of(new EventSessionView(morning, event, "Утро", 1, start, 60),
                new EventSessionView(evening, event, "Вечер", 2, start, 60),
                new EventSessionView(last, event, "Последняя", Integer.MAX_VALUE, start, 60));
        when(events.getById(event)).thenReturn(new EventLookupResult(event, UUID.randomUUID(), "PUBLISHED", null, null, sessions));
        var program = List.of(
                orderedDiscipline("no-session", null, 1, start.minusDays(1), "00000000-0000-0000-0000-000000000004"),
                orderedDiscipline("later-created", morning, null, start.plusDays(1), "00000000-0000-0000-0000-000000000001"),
                orderedDiscipline("high-uuid", morning, null, start, "f0000000-0000-0000-0000-000000000001"),
                orderedDiscipline("last-session", last, null, start, "00000000-0000-0000-0000-000000000003"),
                orderedDiscipline("low-uuid", morning, null, start, "00000000-0000-0000-0000-000000000002"),
                orderedDiscipline("second-session", evening, 1, start, "00000000-0000-0000-0000-000000000005"),
                orderedDiscipline("first-position", morning, 1, start.plusDays(2), "00000000-0000-0000-0000-000000000006"));
        when(disciplines.findPublishedByEventId(event)).thenReturn(program);
        when(results.heats(anyList())).thenAnswer(call -> program.stream()
                .map(d -> new Heat(d.id(), 1, null, "PUBLISHED", start, List.of())).toList());
        var protocol = service.protocol(event, true);
        assertThat(protocol.disciplines()).extracting(d -> d.name()).containsExactly("first-position", "low-uuid",
                "high-uuid", "later-created", "second-session", "last-session", "no-session");
        assertThat(protocol.disciplines()).extracting(d -> d.number()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    private EventDisciplineSummary orderedDiscipline(String name, UUID session, Integer order, OffsetDateTime createdAt, String id) {
        return new EventDisciplineSummary(UUID.fromString(id), event, name, name, "INDIVIDUAL", "HEAT", "TIME", "ASC",
                null, null, null, "{}", 50, "FREE", "M", List.of(), "BY_TIME_ALL", session, order, 90, createdAt);
    }
    private EventDisciplineSummary program(String mode) {
        return new EventDisciplineSummary(discipline, event, "M-50", "50 м", "INDIVIDUAL", "HEAT", "TIME", "ASC",
                null, null, null, "{}", 50, "FREE", "M",
                List.of(new AgeGroup("young", "2014–2015", "M", 2014, 2015), new AgeGroup("older", "2012–2013", "M", 2012, 2013)),
                mode, null, 1, 90, start);
    }
}
