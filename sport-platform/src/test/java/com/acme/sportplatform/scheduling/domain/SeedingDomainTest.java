package com.acme.sportplatform.scheduling.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.*;
import java.util.*;
import java.util.stream.*;

class SeedingDomainTest {
    private static UUID id(int n) {
        return new UUID(0, n);
    }

    private final HeatSeeder seeder = new HeatSeeder();
    private final PoolSpec pool = new PoolSpec(8, 3);
    private final List<AgeGroupSpec> groups =
            List.of(
                    new AgeGroupSpec("M-2014-2015", "2014–2015", "M", 2014, 2015),
                    new AgeGroupSpec("M-2012-2013", "2012–2013", "M", 2012, 2013));

    private DisciplineSpec discipline(String mode, List<AgeGroupSpec> ageGroups) {
        return new DisciplineSpec(id(1000), 1, 50, "FREE", "M", ageGroups, mode);
    }

    private List<SeedEntry> entries(int n) {
        return IntStream.range(0, n)
                .mapToObj(
                        i ->
                                new SeedEntry(
                                        id(i + 1),
                                        id(i + 101),
                                        "Фам" + i,
                                        "Имя",
                                        2014,
                                        "M",
                                        null,
                                        (30L + i) * 1000))
                .toList();
    }

    private SeedingResult seed(List<SeedEntry> entries) {
        return seeder.seed(
                discipline("BY_TIME_ALL", List.of()), entries, pool, new SeedingOptions(false));
    }

    static Stream<Arguments> laneCases() {
        return Stream.of(
                Arguments.of(8, new int[] {4, 5, 3, 6, 2, 7, 1, 8}),
                Arguments.of(6, new int[] {3, 4, 2, 5, 1, 6}),
                Arguments.of(10, new int[] {5, 6, 4, 7, 3, 8, 2, 9, 1, 10}),
                Arguments.of(7, new int[] {4, 5, 3, 6, 2, 7, 1}),
                Arguments.of(1, new int[] {1}));
    }

    @ParameterizedTest
    @MethodSource("laneCases")
    void laneOrder(int lanes, int[] expected) {
        assertArrayEquals(expected, LaneOrder.forLanes(lanes));
    }

    static Stream<Arguments> sizeCases() {
        return Stream.of(
                Arguments.of(17, 8, new int[] {3, 6, 8}),
                Arguments.of(9, 8, new int[] {3, 6}),
                Arguments.of(16, 8, new int[] {8, 8}),
                Arguments.of(11, 8, new int[] {3, 8}),
                Arguments.of(20, 8, new int[] {4, 8, 8}),
                Arguments.of(7, 6, new int[] {3, 4}),
                Arguments.of(13, 6, new int[] {3, 4, 6}),
                Arguments.of(0, 8, new int[] {}),
                Arguments.of(3, 8, new int[] {3}),
                Arguments.of(1, 8, new int[] {1}));
    }

    @ParameterizedTest
    @MethodSource("sizeCases")
    void heatSizes(int n, int lanes, int[] expected) {
        assertArrayEquals(expected, HeatSizer.sizes(n, lanes));
    }

    @Test
    void seventeenSwimmers() {
        SeedingResult result = seed(entries(17));
        assertTrue(result.violations().isEmpty());
        assertEquals(List.of(3, 6, 8), result.heats().stream().map(h -> h.lanes().size()).toList());
        SeededHeat last = result.heats().getLast();
        assertEquals(30000L, lane(last, 4).seedTimeMs());
        assertEquals(31000L, lane(last, 5).seedTimeMs());
        assertEquals(32000L, lane(last, 3).seedTimeMs());
        assertEquals(
                List.of(44000L, 45000L, 46000L),
                result.heats().getFirst().lanes().stream()
                        .map(SeededLane::seedTimeMs)
                        .sorted()
                        .toList());
    }

    private SeededLane lane(SeededHeat heat, int n) {
        return heat.lanes().stream().filter(l -> l.lane() == n).findFirst().orElseThrow();
    }

    @Test
    void noTimesAfterTimedEntriesInFirstHeat() {
        List<SeedEntry> entries = new ArrayList<>(entries(10));
        for (int i = 8; i < 10; i++) {
            SeedEntry e = entries.get(i);
            entries.set(
                    i,
                    new SeedEntry(
                            e.registrationId(),
                            e.athleteId(),
                            e.lastName(),
                            e.firstName(),
                            e.birthYear(),
                            e.gender(),
                            e.club(),
                            null));
        }
        SeedingResult result = seed(entries);
        assertEquals(List.of(3, 7), result.heats().stream().map(h -> h.lanes().size()).toList());
        assertNotNull(lane(result.heats().getFirst(), 4).seedTimeMs());
        assertNull(lane(result.heats().getFirst(), 5).seedTimeMs());
        assertNull(lane(result.heats().getFirst(), 3).seedTimeMs());
    }

    @Test
    void ageGroupsHaveContinuousNumbers() {
        List<SeedEntry> entries =
                entries(10).stream()
                        .map(
                                e ->
                                        new SeedEntry(
                                                e.registrationId(),
                                                e.athleteId(),
                                                e.lastName(),
                                                e.firstName(),
                                                e.registrationId().getLeastSignificantBits() % 2
                                                                == 1
                                                        ? 2014
                                                        : 2012,
                                                e.gender(),
                                                null,
                                                e.seedTimeMs()))
                        .toList();
        SeedingResult result =
                seeder.seed(
                        discipline("BY_AGE_GROUP", groups),
                        entries,
                        pool,
                        new SeedingOptions(false));
        assertEquals(
                List.of(
                        new SeededHeat(1, groups.get(0).code(), result.heats().get(0).lanes()),
                        new SeededHeat(2, groups.get(1).code(), result.heats().get(1).lanes())),
                result.heats());
        assertEquals(List.of(5, 5), result.heats().stream().map(h -> h.lanes().size()).toList());
    }

    @Test
    void rejectsIneligibleAndDuplicates() {
        List<SeedEntry> entries = new ArrayList<>(entries(3));
        SeedEntry e = entries.get(0);
        entries.set(
                0,
                new SeedEntry(
                        e.registrationId(),
                        e.athleteId(),
                        e.lastName(),
                        e.firstName(),
                        2014,
                        "F",
                        null,
                        e.seedTimeMs()));
        e = entries.get(1);
        entries.set(
                1,
                new SeedEntry(
                        e.registrationId(),
                        e.athleteId(),
                        e.lastName(),
                        e.firstName(),
                        2007,
                        "M",
                        null,
                        e.seedTimeMs()));
        e = entries.get(2);
        entries.add(
                new SeedEntry(
                        id(999),
                        e.athleteId(),
                        e.lastName(),
                        e.firstName(),
                        2014,
                        "M",
                        null,
                        1000L));
        SeedingResult result =
                seeder.seed(
                        discipline("BY_TIME_ALL", groups),
                        entries,
                        pool,
                        new SeedingOptions(false));
        assertEquals(
                Set.of("GENDER_MISMATCH", "AGE_GROUP_MISMATCH", "DUPLICATE_ENTRY"),
                result.violations().stream().map(Violation::code).collect(Collectors.toSet()));
        assertEquals(3, result.excludedRegistrationIds().size());
        assertEquals(1, result.heats().getFirst().lanes().size());
    }

    @Test
    void rejectsMissingAthlete() {
        SeedingResult result =
                seed(List.of(new SeedEntry(id(1), null, null, null, null, null, null, null)));
        assertTrue(
                result.violations().stream().anyMatch(v -> v.code().equals("ATHLETE_NOT_FOUND")));
        assertEquals(List.of(id(1)), result.excludedRegistrationIds());
    }

    @Test
    void deterministicIncludingTiesAndRussianNames() {
        List<SeedEntry> entries = new ArrayList<>(entries(37));
        for (int i = 0; i < entries.size(); i++) {
            SeedEntry e = entries.get(i);
            entries.set(
                    i,
                    new SeedEntry(
                            e.registrationId(),
                            e.athleteId(),
                            i % 2 == 0 ? "Алексеев" : "Яковлев",
                            "Имя",
                            2014,
                            "M",
                            null,
                            30000L));
        }
        SeedingResult a = seed(entries);
        Collections.reverse(entries);
        SeedingResult b = seed(entries);
        assertEquals(a, b);
        assertEquals(
                "Алексеев",
                entries.stream()
                        .filter(
                                e ->
                                        e.registrationId()
                                                .equals(
                                                        lane(a.heats().getLast(), 4)
                                                                .registrationId()))
                        .findFirst()
                        .orElseThrow()
                        .lastName());
    }

    @Test
    void performance() {
        assertTimeout(
                Duration.ofSeconds(5),
                () -> {
                    for (int k = 0; k < 4; k++) assertEquals(25, seed(entries(200)).heats().size());
                });
    }

    @Test
    void validatorDetectsLaneConflictAndDoubleAssignment() {
        List<SeedEntry> entries = entries(17);
        List<SeededHeat> heats = new ArrayList<>(seed(entries).heats());
        SeededLane source = heats.getLast().lanes().getFirst();
        List<SeededLane> first = new ArrayList<>(heats.getFirst().lanes());
        first.set(
                0,
                new SeededLane(
                        4, source.registrationId(), source.athleteId(), source.seedTimeMs()));
        first.set(
                1,
                new SeededLane(
                        4,
                        first.get(1).registrationId(),
                        first.get(1).athleteId(),
                        first.get(1).seedTimeMs()));
        heats.set(0, new SeededHeat(1, null, first));
        List<Violation> violations =
                new SeedingValidator()
                        .validate(
                                discipline("BY_TIME_ALL", List.of()),
                                pool,
                                entries,
                                heats,
                                List.of(),
                                SchedulingRules.defaults());
        assertTrue(
                violations.stream()
                        .anyMatch(
                                v ->
                                        v.code().equals("LANE_CONFLICT")
                                                && v.severity() == Violation.Severity.ERROR));
        assertTrue(
                violations.stream()
                        .anyMatch(
                                v ->
                                        v.code().equals("ATHLETE_IN_TWO_HEATS")
                                                && v.severity() == Violation.Severity.ERROR));
    }

    @Test
    void correctAssignmentsValidate() {
        List<SeedEntry> entries = entries(17);
        assertTrue(
                new SeedingValidator()
                        .validate(
                                discipline("BY_TIME_ALL", List.of()),
                                pool,
                                entries,
                                seed(entries).heats(),
                                List.of(),
                                SchedulingRules.defaults())
                        .isEmpty());
    }

    @Test
    void circleTopThreeAndFallback() {
        var d = discipline("BY_TIME_ALL", List.of());
        var circle = seeder.seed(d, entries(24), pool, new SeedingOptions(true));
        assertEquals(30000L, lane(circle.heats().get(2), 4).seedTimeMs());
        assertEquals(31000L, lane(circle.heats().get(1), 4).seedTimeMs());
        assertEquals(32000L, lane(circle.heats().get(0), 4).seedTimeMs());
        assertEquals(33000L, lane(circle.heats().get(2), 5).seedTimeMs());
        assertEquals(
                seed(entries(17)), seeder.seed(d, entries(17), pool, new SeedingOptions(true)));
    }

    @Test
    void scheduleExample() {
        UUID session = id(500);
        Instant start = OffsetDateTime.parse("2026-10-17T10:00:00+04:00").toInstant();
        var d50 =
                new DisciplineSpec(
                        id(50), 1, 50, "FREE", "M", List.of(), "BY_TIME_ALL", session, 1, null);
        var d100 =
                new DisciplineSpec(
                        id(100), 2, 100, "FREE", "M", List.of(), "BY_TIME_ALL", session, 2, null);
        List<ScheduledHeat> schedule =
                new ScheduleBuilder()
                        .build(
                                List.of(new SessionSpec(session, 1, start, 60)),
                                List.of(d100, d50),
                                Map.of(
                                        d50.id(),
                                                IntStream.rangeClosed(1, 3)
                                                        .mapToObj(
                                                                n ->
                                                                        new SeededHeat(
                                                                                n, null, List.of()))
                                                        .toList(),
                                        d100.id(),
                                                IntStream.rangeClosed(1, 2)
                                                        .mapToObj(
                                                                n ->
                                                                        new SeededHeat(
                                                                                n, null, List.of()))
                                                        .toList()),
                                SchedulingRules.defaults());
        assertEquals(
                List.of(0L, 90L, 180L, 330L, 480L),
                schedule.stream()
                        .map(h -> Duration.between(start, h.scheduledAt()).getSeconds())
                        .toList());
        assertEquals(
                List.of(1, 2, 3, 4, 5),
                schedule.stream().map(ScheduledHeat::sequenceInSession).toList());
    }

    @Test
    void scheduleDurationOverrideAndRestWarning() {
        UUID session = id(500);
        Instant start = Instant.parse("2026-10-17T06:00:00Z");
        var d =
                new DisciplineSpec(
                        id(50), 1, 50, "FREE", "M", List.of(), "BY_TIME_ALL", session, 1, 42);
        var heat = new SeededHeat(1, null, List.of(new SeededLane(4, id(1), id(101), null)));
        var scheduled =
                new ScheduleBuilder()
                        .build(
                                List.of(new SessionSpec(session, 1, start, 60)),
                                List.of(d),
                                Map.of(
                                        d.id(),
                                        List.of(heat, new SeededHeat(2, null, heat.lanes()))),
                                SchedulingRules.defaults());
        assertEquals(start.plusSeconds(42), scheduled.get(1).scheduledAt());
        assertTrue(
                new SeedingValidator()
                                .validate(
                                        d,
                                        pool,
                                        entries(1),
                                        List.of(heat),
                                        scheduled,
                                        SchedulingRules.defaults())
                                .stream()
                                .anyMatch(v -> v.code().equals("INSUFFICIENT_REST")));
    }

    @Test
    void rankingTiesAndDns() {
        List<ResultRanker.ResultEntry> entries =
                List.of(
                        new ResultRanker.ResultEntry(
                                id(1), id(101), 2014, "M", 30000L, "VALID", null),
                        new ResultRanker.ResultEntry(
                                id(2), id(102), 2014, "M", 30000L, "VALID", null),
                        new ResultRanker.ResultEntry(
                                id(3), id(103), 2014, "M", 31000L, "VALID", null),
                        new ResultRanker.ResultEntry(
                                id(4), id(104), 2014, "M", null, "DID_NOT_START", null));
        assertEquals(
                List.of(
                        new ResultRanker.RankedResult(id(1), 1, 1),
                        new ResultRanker.RankedResult(id(2), 1, 1),
                        new ResultRanker.RankedResult(id(3), 3, 3),
                        new ResultRanker.RankedResult(id(4), null, null)),
                new ResultRanker().rank(entries, groups));
    }

    @Test
    void validatorChecksAllStructuralAndEligibilityErrors() {
        var d = discipline("BY_AGE_GROUP", groups);
        var invalidEntries =
                List.of(
                        new SeedEntry(id(1), id(101), "Фам", "Имя", 2014, "F", null, 30000L),
                        new SeedEntry(id(2), id(102), "Фам", "Имя", 2007, "M", null, 30000L));
        var lanes = new ArrayList<SeededLane>();
        lanes.add(new SeededLane(0, id(1), id(101), 30000L));
        lanes.add(new SeededLane(9, id(2), id(102), 30000L));
        for (int n = 3; n <= 9; n++) lanes.add(new SeededLane(n, id(n), id(100 + n), 30000L));
        var violations =
                new SeedingValidator()
                        .validate(
                                d,
                                pool,
                                invalidEntries,
                                List.of(new SeededHeat(2, "M-2014-2015", lanes)),
                                List.of(),
                                SchedulingRules.defaults());
        var codes =
                violations.stream()
                        .filter(v -> v.severity() == Violation.Severity.ERROR)
                        .map(Violation::code)
                        .collect(Collectors.toSet());
        assertTrue(
                codes.containsAll(
                        Set.of(
                                "HEAT_NUMBER_SEQUENCE",
                                "HEAT_OVER_CAPACITY",
                                "LANE_OUT_OF_RANGE",
                                "ATHLETE_NOT_FOUND",
                                "GENDER_MISMATCH",
                                "AGE_GROUP_MISMATCH")));
    }

    @Test
    void validatorSmallHeatExceptionAndNtMajority() {
        var d = discipline("BY_TIME_ALL", List.of());
        var entries = entries(4);
        var small =
                new SeededHeat(
                        1,
                        null,
                        List.of(
                                new SeededLane(
                                        4,
                                        entries.get(0).registrationId(),
                                        entries.get(0).athleteId(),
                                        null)));
        var validator = new SeedingValidator();
        var alone =
                validator.validate(
                        d, pool, entries, List.of(small), List.of(), SchedulingRules.defaults());
        assertTrue(alone.stream().noneMatch(v -> v.code().equals("SMALL_HEAT")));
        assertTrue(alone.stream().anyMatch(v -> v.code().equals("MANY_NT")));
        var second =
                new SeededHeat(
                        2,
                        null,
                        entries.subList(1, 4).stream()
                                .map(
                                        e ->
                                                new SeededLane(
                                                        (int)
                                                                e.registrationId()
                                                                        .getLeastSignificantBits(),
                                                        e.registrationId(),
                                                        e.athleteId(),
                                                        e.seedTimeMs()))
                                .toList());
        var enough =
                validator.validate(
                        d,
                        pool,
                        entries,
                        List.of(small, second),
                        List.of(),
                        SchedulingRules.defaults());
        assertTrue(enough.stream().anyMatch(v -> v.code().equals("SMALL_HEAT")));
        assertTrue(enough.stream().noneMatch(v -> v.code().equals("MANY_NT")));
    }

    @Test
    void rankingSeparatesGroupsAndUnrankedStatuses() {
        var entries =
                List.of(
                        new ResultRanker.ResultEntry(
                                id(1), id(101), 2014, "M", 30000L, "VALID", null),
                        new ResultRanker.ResultEntry(
                                id(2), id(102), 2012, "M", 31000L, "VALID", null),
                        new ResultRanker.ResultEntry(
                                id(3), id(103), 2014, "M", 32000L, "VALID", null),
                        new ResultRanker.ResultEntry(
                                id(4), id(104), 2014, "M", 20000L, "DISQUALIFIED", null),
                        new ResultRanker.ResultEntry(
                                id(5), id(105), 2014, "M", 20000L, "DID_NOT_FINISH", null),
                        new ResultRanker.ResultEntry(
                                id(6), id(106), 2014, "M", 20000L, "EXHIBITION", null));
        var ranked = new ResultRanker().rank(entries, groups);
        assertEquals(new ResultRanker.RankedResult(id(1), 1, 1), ranked.get(0));
        assertEquals(new ResultRanker.RankedResult(id(2), 2, 1), ranked.get(1));
        assertEquals(new ResultRanker.RankedResult(id(3), 3, 2), ranked.get(2));
        for (int n = 3; n < 6; n++) {
            assertNull(ranked.get(n).finalPlace());
            assertNull(ranked.get(n).placeInAgeGroup());
        }
    }

    @Test
    void admissionMessagesIdentifyAthleteAndDisciplineInBothImplementations() {
        var d = new DisciplineSpec(id(1000), 3, 50, "FREE", "M", groups, "BY_AGE_GROUP");
        var wrongGender = new SeedEntry(id(1), id(101), "Иванова", "Анна", 2014, "F", null, 30000L);
        var wrongAge = new SeedEntry(id(2), id(102), "Петров", "Иван", 2007, "M", null, 31000L);
        var missing = new SeedEntry(id(3), null, "Сидоров", "Пётр", 2014, "M", null, null);
        var valid = new SeedEntry(id(4), id(104), "Орлов", "Олег", 2014, "M", null, 32000L);
        var duplicate =
                new SeedEntry(
                        id(5),
                        valid.athleteId(),
                        valid.lastName(),
                        valid.firstName(),
                        valid.birthYear(),
                        valid.gender(),
                        null,
                        33000L);
        var entries = List.of(wrongGender, wrongAge, missing, valid, duplicate);
        var generated = seeder.seed(d, entries, pool, new SeedingOptions(false));
        assertEquals(
                "Иванова Анна (2014): пол не соответствует виду №3",
                generated.violations().stream()
                        .filter(v -> v.code().equals("GENDER_MISMATCH"))
                        .findFirst()
                        .orElseThrow()
                        .message());
        var heat =
                new SeededHeat(
                        1,
                        groups.getFirst().code(),
                        entries.stream()
                                .map(
                                        e ->
                                                new SeededLane(
                                                        (int)
                                                                e.registrationId()
                                                                        .getLeastSignificantBits(),
                                                        e.registrationId(),
                                                        e.athleteId(),
                                                        e.seedTimeMs()))
                                .toList());
        var checked =
                new SeedingValidator()
                        .validate(
                                d,
                                pool,
                                entries,
                                List.of(heat),
                                List.of(),
                                SchedulingRules.defaults());
        for (var violations : List.of(generated.violations(), checked)) {
            for (Violation v : violations)
                if (Set.of(
                                "ATHLETE_NOT_FOUND",
                                "DUPLICATE_ENTRY",
                                "ATHLETE_IN_TWO_HEATS",
                                "GENDER_MISMATCH",
                                "AGE_GROUP_MISMATCH")
                        .contains(v.code())) {
                    var e =
                            entries.stream()
                                    .filter(x -> x.registrationId().equals(v.registrationId()))
                                    .findFirst()
                                    .orElseThrow();
                    assertTrue(v.message().contains(e.athleteLabel()));
                    assertTrue(v.message().contains("№3"));
                }
        }
        var repeated =
                new SeededHeat(
                        1,
                        groups.getFirst().code(),
                        List.of(
                                new SeededLane(
                                        4,
                                        valid.registrationId(),
                                        valid.athleteId(),
                                        valid.seedTimeMs()),
                                new SeededLane(
                                        5,
                                        valid.registrationId(),
                                        valid.athleteId(),
                                        valid.seedTimeMs())));
        var duplicateViolation =
                new SeedingValidator()
                                .validate(
                                        d,
                                        pool,
                                        entries,
                                        List.of(repeated),
                                        List.of(),
                                        SchedulingRules.defaults())
                                .stream()
                                .filter(v -> v.code().equals("DUPLICATE_ENTRY"))
                                .findFirst()
                                .orElseThrow();
        assertTrue(duplicateViolation.message().contains("Орлов Олег (2014)"));
        assertTrue(duplicateViolation.message().contains("№3"));
    }
}
