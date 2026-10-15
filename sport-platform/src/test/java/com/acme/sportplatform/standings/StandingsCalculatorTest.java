package com.acme.sportplatform.standings;

import com.acme.sportplatform.competition.*;
import com.acme.sportplatform.results.ResultsProtocolLookup.*;
import com.acme.sportplatform.standings.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StandingsCalculatorTest {
    private final UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final StandingsCalculator calculator = new StandingsCalculator(
            Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC));
    private StandingsConfig config(StandingsConfig.Scope scope, Integer limit, List<UUID> ids) {
        return new StandingsConfig(true, scope, List.of(9,7,6,5,4,3,2,1), limit, ids, true, "Кубок клубов");
    }
    private EventDisciplineSummary discipline(UUID id, String mode) {
        return new EventDisciplineSummary(id, UUID.randomUUID(), "50", "50 м", "INDIVIDUAL", "HEAT", "TIME", "TIME_ASC",
                null, null, null, "{}", 50, "FREE", "X",
                List.of(new AgeGroup("young", "Младшие", "X", 2013, 2015), new AgeGroup("older", "Старшие", "X", 2010, 2012)),
                mode, null, null, null, null);
    }
    private Entry entry(String club, int year, int absolute, int grouped) {
        return new Entry(1, "Иванов", "Иван", year, "MALE", club, null, false,
                new Outcome(32000L, "VALID", "00:32.00", absolute, grouped));
    }
    private Heat heat(UUID id, String group, String status, Entry... entries) {
        return new Heat(id, 1, group, status, null, List.of(entries));
    }
    @Test
    void threeClubsAcrossTwoDisciplinesAndTwoGroupsHaveHandCalculatedTotals() {
        var heats = List.of(
                heat(first, "young", "FINISHED", entry("Волна", 2014, 1, 1), entry("Темп", 2014, 2, 2), entry("Старт", 2014, 3, 3)),
                heat(first, "older", "FINISHED", entry("Темп", 2011, 4, 1), entry("Старт", 2011, 5, 2), entry("Волна", 2011, 6, 3)),
                heat(second, "young", "COMPLETED", entry("Старт", 2014, 1, 1), entry("Волна", 2014, 2, 2), entry("Темп", 2014, 3, 3)),
                heat(second, "older", "FINISHED", entry("Волна", 2011, 4, 1), entry("Темп", 2011, 5, 2), entry("Старт", 2011, 6, 3)));
        var result = calculator.calculate(config(StandingsConfig.Scope.AGE_GROUP, null, null), heats,
                List.of(discipline(first, "BY_AGE_GROUP"), discipline(second, "BY_AGE_GROUP")));
        // Wave: 9+6+7+9=31; Tempo: 7+9+6+7=29; Start: 6+7+9+6=28.
        assertThat(result.team()).extracting(StandingsView.ClubPoints::points).containsExactly(31L,29L,28L);
        assertThat(result.medals()).containsExactly(new StandingsView.ClubMedals("Волна",2,1,1),
                new StandingsView.ClubMedals("Темп",1,2,1), new StandingsView.ClubMedals("Старт",1,1,2));
        assertThat(result.unitsPending()).isZero();
        assertThat(result.disciplinesIncluded()).isEqualTo(2);
    }
    @Test
    void tiesAreNotRerankedAndNonFinishersNeverScoreEvenWithStalePlaces() {
        var dsq = new Entry(4,"Дисквалифицирован","Иван",2014,"M","Темп",null,false,
                new Outcome(null,"DISQUALIFIED",null,1,1));
        var dns = new Entry(5,"Не стартовал","Иван",2014,"M","Темп",null,false,
                new Outcome(null,"CORRECTED","DID_NOT_START",1,1));
        var result = calculator.calculate(config(StandingsConfig.Scope.AGE_GROUP, null, null), List.of(
                heat(first,"young","FINISHED",entry("Волна",2014,1,1),entry("Волна",2014,1,1),entry("Волна",2014,3,3),dsq,dns)),
                List.of(discipline(first,"BY_AGE_GROUP")));
        var wave = result.team().getFirst();
        assertThat(wave.points()).isEqualTo(24);
        assertThat(wave.breakdown()).extracting(StandingsView.Breakdown::points).containsExactly(9,9,6);
        assertThat(wave.gold()).isEqualTo(2);
        assertThat(wave.bronze()).isEqualTo(1);
        assertThat(result.team().get(1).points()).isZero();
        assertThat(result.team().get(1).gold()).isZero();
    }
    @Test
    void capUsesBestResultsWithinEachDisciplineAndAgeGroupIncludingMixedHeats() {
        var entries = new Entry[]{entry("Волна",2014,5,3),entry("Волна",2014,1,1),entry("Волна",2014,3,2),
                entry("Волна",2011,2,1),entry("Волна",2011,4,2),entry("Волна",2011,6,3)};
        var result = calculator.calculate(config(StandingsConfig.Scope.AGE_GROUP,2,null), List.of(heat(first,null,"FINISHED",entries)),
                List.of(discipline(first,"BY_TIME_ALL")));
        assertThat(result.team().getFirst().points()).isEqualTo(32); // (9+7) in each group
        assertThat(result.team().getFirst().scoredResults()).isEqualTo(4);
        assertThat(result.team().getFirst().breakdown()).extracting(StandingsView.Breakdown::ageGroupCode)
                .containsExactly("older","older","young","young");
        assertThat(result.medals().getFirst().bronze()).isEqualTo(2); // Medal totals are independent of scoring caps.
        var absolute = calculator.calculate(config(StandingsConfig.Scope.ABSOLUTE,2,null), List.of(heat(first,null,"FINISHED",entries)),
                List.of(discipline(first,"BY_TIME_ALL")));
        assertThat(absolute.team().getFirst().points()).isEqualTo(16);
        assertThat(absolute.team().getFirst().scoredResults()).isEqualTo(2);
    }
    @Test
    void normalizesClubsSelectsMostFrequentSpellingAndIncludesEmptyClub() {
        var result = calculator.calculate(config(StandingsConfig.Scope.AGE_GROUP,null,null), List.of(heat(first,"young","FINISHED",
                entry("Волна  ",2014,1,1), entry("волна",2014,2,2), entry("волна",2014,3,3),
                entry("  СШОР   Волна  ",2014,4,4), entry("СШОР Волна",2014,5,5),entry(" ",2014,6,6),entry(null,2014,7,7))),
                List.of(discipline(first,"BY_AGE_GROUP")));
        assertThat(result.team()).extracting(StandingsView.ClubPoints::club).containsExactly("волна","СШОР Волна","Без клуба");
        assertThat(result.team().getFirst().points()).isEqualTo(22);
        assertThat(result.team().get(2).points()).isEqualTo(5);
    }
    @Test
    void skipsPendingHeatsAndExcludedDisciplinesAndProducesStableJsonRegardlessOfInputOrder() throws Exception {
        var program = List.of(discipline(first,"BY_AGE_GROUP"),discipline(second,"BY_AGE_GROUP"));
        var heats = List.of(heat(first,"young","FINISHED",entry("Волна",2014,1,1)),
                heat(first,"older","PUBLISHED",entry("Темп",2011,1,1)),
                heat(second,"young","IN_PROGRESS",entry("Старт",2014,1,1)));
        var config = config(StandingsConfig.Scope.AGE_GROUP,null,List.of(first));
        var result = calculator.calculate(config, heats, program);
        assertThat(result.unitsPending()).isEqualTo(1);
        assertThat(result.preliminary()).isTrue();
        assertThat(result.disciplinesIncluded()).isEqualTo(1);
        assertThat(result.team()).extracting(StandingsView.ClubPoints::club).containsExactly("Волна");
        var json = new ObjectMapper().findAndRegisterModules();
        var reversed = new ArrayList<>(heats); Collections.reverse(reversed);
        assertThat(json.writeValueAsString(result)).isEqualTo(json.writeValueAsString(calculator.calculate(config,reversed,program)));
        assertThat(json.writeValueAsString(result)).isEqualTo(json.writeValueAsString(calculator.calculate(config,heats,program)));
    }
    @Test
    void pendingAndMissingOutcomesDoNotCreatePublicClubRowsEvenInFinishedHeats() {
        var missing = new Entry(1,"Не финишировал","Иван",2014,"M","Черновой клуб",null,false,null);
        var pending = new Entry(2,"Ожидает","Иван",2014,"M","Ожидающий клуб",null,false,
                new Outcome(null,"PENDING",null,null,null));
        var result = calculator.calculate(config(StandingsConfig.Scope.AGE_GROUP,null,null),
                List.of(heat(first,"young","FINISHED",missing,pending)),List.of(discipline(first,"BY_AGE_GROUP")));
        assertThat(result.team()).isEmpty();
        assertThat(result.medals()).isEmpty();
    }
    @Test
    void pointsUseLongAndRankingBreaksTiesByMedalsThenClubName() {
        var config = new StandingsConfig(true, StandingsConfig.Scope.AGE_GROUP,List.of(Integer.MAX_VALUE,Integer.MAX_VALUE),null,null,true,null);
        var result = calculator.calculate(config,List.of(heat(first,"young","FINISHED",entry("Б",2014,1,1),entry("А",2014,2,2),entry("Б",2014,3,2))),
                List.of(discipline(first,"BY_AGE_GROUP")));
        assertThat(result.team().getFirst().points()).isEqualTo(4294967294L);
        assertThat(result.team().getFirst().club()).isEqualTo("Б");
    }
}
