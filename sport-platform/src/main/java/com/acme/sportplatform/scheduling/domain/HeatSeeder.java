package com.acme.sportplatform.scheduling.domain;

import static com.acme.sportplatform.scheduling.domain.Violation.Severity.*;

import java.text.Collator;
import java.util.*;

public final class HeatSeeder {
    public SeedingResult seed(
            DisciplineSpec discipline,
            List<SeedEntry> entries,
            PoolSpec pool,
            SeedingOptions options) {
        List<Violation> issues = new ArrayList<>();
        List<UUID> excluded = new ArrayList<>();
        List<SeedEntry> eligible = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (SeedEntry entry : entries) {
            String code = null, message = null;
            if (entry.athleteId() == null || entry.birthYear() == null || entry.gender() == null) {
                code = "ATHLETE_NOT_FOUND";
                message = "Заявка: спортсмен не найден";
            } else if (seen.contains(entry.athleteId())) {
                code = "DUPLICATE_ENTRY";
                message = "Повторная заявка спортсмена на вид";
            } else if (!"X".equals(discipline.gender())
                    && !discipline.gender().equals(entry.gender())) {
                code = "GENDER_MISMATCH";
                message = "Пол спортсмена не соответствует виду";
            } else if (!discipline.ageGroups().isEmpty()
                    && group(entry, discipline.ageGroups()) == null) {
                code = "AGE_GROUP_MISMATCH";
                message = "Спортсмен не подходит ни под одну возрастную группу вида";
            }
            if (code != null) {
                issues.add(
                        new Violation(
                                code,
                                ERROR,
                                message,
                                discipline.id(),
                                null,
                                entry.athleteId(),
                                entry.registrationId()));
                excluded.add(entry.registrationId());
            } else {
                seen.add(entry.athleteId());
                eligible.add(entry);
            }
        }
        Collator ru = Collator.getInstance(Locale.forLanguageTag("ru"));
        Comparator<SeedEntry> comparator =
                Comparator.comparing(SeedEntry::seedTimeMs, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(
                                e ->
                                        Objects.toString(e.lastName(), "")
                                                + " "
                                                + Objects.toString(e.firstName(), ""),
                                ru)
                        .thenComparing(e -> e.registrationId().toString());
        List<SeededHeat> heats = new ArrayList<>();
        if ("BY_AGE_GROUP".equals(discipline.seedingMode()) && !discipline.ageGroups().isEmpty()) {
            for (AgeGroupSpec group : discipline.ageGroups()) {
                List<SeedEntry> list =
                        eligible.stream()
                                .filter(e -> group.equals(group(e, discipline.ageGroups())))
                                .sorted(comparator)
                                .toList();
                if (list.isEmpty()) continue;
                if (list.size() < pool.minSwimmersInHeat())
                    issues.add(
                            new Violation(
                                    "SMALL_GROUP",
                                    WARNING,
                                    "Мало участников в группе «"
                                            + group.name()
                                            + "»: "
                                            + list.size(),
                                    discipline.id(),
                                    null,
                                    null,
                                    null));
                append(
                        heats,
                        list,
                        group.code(),
                        pool,
                        (options != null && options.circleSeedTopHeats()));
            }
        } else
            append(
                    heats,
                    eligible.stream().sorted(comparator).toList(),
                    null,
                    pool,
                    (options != null && options.circleSeedTopHeats()));
        if (heats.isEmpty())
            issues.add(
                    new Violation(
                            "NO_ENTRIES",
                            WARNING,
                            "Нет допущенных заявок",
                            discipline.id(),
                            null,
                            null,
                            null));
        return new SeedingResult(heats, issues, excluded);
    }

    private AgeGroupSpec group(SeedEntry entry, List<AgeGroupSpec> groups) {
        return groups.stream().filter(g -> g.contains(entry)).findFirst().orElse(null);
    }

    private void append(
            List<SeededHeat> out,
            List<SeedEntry> sorted,
            String groupCode,
            PoolSpec pool,
            boolean circle) {
        int[] sizes = HeatSizer.sizes(sorted.size(), pool.lanes(), pool.minSwimmersInHeat());
        int[] order = LaneOrder.forLanes(pool.lanes());
        List<List<SeededLane>> heats = new ArrayList<>();
        for (int ignored : sizes) heats.add(new ArrayList<>());
        int cursor = 0, last = sizes.length - 1;
        boolean topCircle =
                circle
                        && sizes.length >= 3
                        && sizes[last] == pool.lanes()
                        && sizes[last - 1] == pool.lanes()
                        && sizes[last - 2] == pool.lanes();
        if (topCircle) {
            for (int lane = 0; lane < pool.lanes(); lane++)
                for (int heat = last; heat >= last - 2; heat--)
                    assign(heats.get(heat), sorted.get(cursor++), order[lane]);
        }
        for (int heat = topCircle ? last - 3 : last; heat >= 0; heat--)
            for (int lane = 0; lane < sizes[heat]; lane++)
                assign(heats.get(heat), sorted.get(cursor++), order[lane]);
        for (List<SeededLane> lanes : heats) {
            lanes.sort(Comparator.comparingInt(SeededLane::lane));
            out.add(new SeededHeat(out.size() + 1, groupCode, lanes));
        }
    }

    private void assign(List<SeededLane> lanes, SeedEntry entry, int lane) {
        lanes.add(
                new SeededLane(
                        lane, entry.registrationId(), entry.athleteId(), entry.seedTimeMs()));
    }
}
