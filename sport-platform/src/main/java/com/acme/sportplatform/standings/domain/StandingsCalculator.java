package com.acme.sportplatform.standings.domain;

import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.results.ResultsProtocolLookup.Heat;
import com.acme.sportplatform.results.ResultsProtocolLookup.Entry;
import com.acme.sportplatform.standings.StandingsView.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Consumes existing places without reranking. The clock is explicit to make calculations reproducible. */
public final class StandingsCalculator {
    private final Clock clock;
    public StandingsCalculator() { this(Clock.systemUTC()); }
    public StandingsCalculator(Clock clock) { this.clock = clock; }

    private static final class Club {
        final Map<String, Integer> spellings = new TreeMap<>();
        final List<Breakdown> results = new ArrayList<>();
        int gold, silver, bronze;
        String name() {
            return spellings.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey())).findFirst().orElseThrow().getKey();
        }
    }

    public StandingsResult calculate(StandingsConfig config, List<Heat> heats, List<EventDisciplineSummary> disciplines) {
        var included = disciplines.stream().filter(d -> config.disciplineIds() == null || config.disciplineIds().contains(d.id()))
                .collect(Collectors.toMap(EventDisciplineSummary::id, Function.identity()));
        Map<String, Club> clubs = new TreeMap<>();
        int pending = 0;
        for (var heat : heats) {
            var discipline = included.get(heat.disciplineId());
            if (discipline == null) continue;
            if (!Set.of("FINISHED", "COMPLETED").contains(heat.status())) { pending++; continue; }
            for (var entry : heat.entries()) {
                if (entry.withdrawn() || entry.outcome() == null || "PENDING".equals(entry.outcome().status())) continue;
                String name = normalize(entry.club());
                var club = clubs.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new Club());
                club.spellings.merge(name, 1, Integer::sum);
                if (!ranked(entry)) continue;
                var outcome = entry.outcome();
                boolean grouped = config.scope() == StandingsConfig.Scope.AGE_GROUP;
                Integer place = grouped ? outcome.placeInAgeGroup() : outcome.place();
                if (place == null || place < 1) continue;
                if (place == 1) club.gold++;
                if (place == 2) club.silver++;
                if (place == 3) club.bronze++;
                int points = place <= config.pointsByPlace().size() ? config.pointsByPlace().get(place - 1) : 0;
                club.results.add(new Breakdown(heat.disciplineId(), grouped ? group(entry, heat, discipline) : null,
                        place, points, Objects.toString(entry.lastName(), "") + " " + Objects.toString(entry.firstName(), ""), true, entry.profileId()));
            }
        }
        var medals = clubs.values().stream().map(c -> new ClubMedals(c.name(), c.gold, c.silver, c.bronze))
                .sorted(Comparator.comparingInt(ClubMedals::gold).reversed()
                        .thenComparing(Comparator.comparingInt(ClubMedals::silver).reversed())
                        .thenComparing(Comparator.comparingInt(ClubMedals::bronze).reversed()).thenComparing(ClubMedals::club)).toList();
        var team = new ArrayList<ClubPoints>();
        var resultOrder = Comparator.comparing((Breakdown b) -> b.disciplineId().toString())
                .thenComparing(Breakdown::ageGroupCode, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(Breakdown::place).thenComparing(Breakdown::athlete);
        for (var club : clubs.values()) {
            var ordered = club.results.stream().sorted(resultOrder).toList();
            Map<List<String>, Integer> counts = new HashMap<>();
            var breakdown = ordered.stream().map(b -> {
                int count = counts.merge(List.of(b.disciplineId().toString(), Objects.toString(b.ageGroupCode(), "")), 1, Integer::sum);
                boolean counted = config.maxScoredPerClubPerDiscipline() == null
                        || count <= config.maxScoredPerClubPerDiscipline();
                return counted ? b : new Breakdown(b.disciplineId(), b.ageGroupCode(), b.place(), 0, b.athlete(), false, b.profileId());
            }).toList();
            var scored = breakdown.stream().filter(Breakdown::counted).toList();
            team.add(new ClubPoints(club.name(), scored.stream().mapToLong(Breakdown::points).sum(),
                    club.gold, club.silver, club.bronze, scored.size(), breakdown));
        }
        team.sort(Comparator.comparingLong(ClubPoints::points).reversed()
                .thenComparing(Comparator.comparingInt(ClubPoints::gold).reversed())
                .thenComparing(Comparator.comparingInt(ClubPoints::silver).reversed())
                .thenComparing(Comparator.comparingInt(ClubPoints::bronze).reversed()).thenComparing(ClubPoints::club));
        return new StandingsResult(medals, team, pending, included.size(), clock.instant());
    }

    private static String normalize(String club) {
        String value = club == null ? "" : club.replaceAll("(?U)\\s+", " ").trim();
        return value.isEmpty() ? "Без клуба" : value;
    }

    private static boolean ranked(Entry e) {
        var r = e.outcome();
        return r != null && ("VALID".equals(r.status()) || "OK".equals(r.status())
                || ("CORRECTED".equals(r.status()) && r.timeMs() != null));
    }

    private static String group(Entry e, Heat h, EventDisciplineSummary d) {
        if ("BY_AGE_GROUP".equals(d.seedingMode()) && h.ageGroupCode() != null) return h.ageGroupCode();
        String gender = switch (Objects.toString(e.gender(), "")) {
            case "MALE", "М" -> "M";
            case "FEMALE", "Ж" -> "F";
            default -> e.gender();
        };
        if (e.birthYear() == null) return null;
        return d.ageGroups().stream().filter(g -> g.includes(gender, e.birthYear()))
                .map(g -> g.code()).findFirst().orElse(null);
    }
}
