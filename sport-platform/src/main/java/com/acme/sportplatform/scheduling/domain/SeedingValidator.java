package com.acme.sportplatform.scheduling.domain;

import static com.acme.sportplatform.scheduling.domain.Violation.Severity.*;

import java.time.Duration;
import java.util.*;

/** Independent checks of the supplied assignments; never calls the seeder or heat sizer. */
public final class SeedingValidator {
    public List<Violation> validate(
            DisciplineSpec discipline,
            PoolSpec pool,
            List<SeedEntry> entries,
            List<SeededHeat> heats,
            List<ScheduledHeat> schedule,
            SchedulingRules rules) {
        List<Violation> out = new ArrayList<>();
        Map<UUID, SeedEntry> byRegistration = new HashMap<>();
        for (SeedEntry entry : entries) byRegistration.put(entry.registrationId(), entry);
        Set<UUID> athletes = new HashSet<>(), registrations = new HashSet<>();
        Map<String, Integer> groupTotals = new HashMap<>();
        for (SeededHeat heat : heats)
            groupTotals.merge(
                    Objects.toString(heat.ageGroupCode(), ""), heat.lanes().size(), Integer::sum);
        int expectedNumber = 1, nt = 0, total = 0;
        for (SeededHeat heat :
                heats.stream().sorted(Comparator.comparingInt(SeededHeat::number)).toList()) {
            if (heat.number() != expectedNumber++)
                add(
                        out,
                        "HEAT_NUMBER_SEQUENCE",
                        ERROR,
                        "Номера заплывов должны идти подряд с 1",
                        discipline,
                        heat,
                        null);
            if (heat.lanes().size() > pool.lanes())
                add(
                        out,
                        "HEAT_OVER_CAPACITY",
                        ERROR,
                        "Участников больше, чем дорожек",
                        discipline,
                        heat,
                        null);
            if (heat.lanes().size() < pool.minSwimmersInHeat()
                    && groupTotals.get(Objects.toString(heat.ageGroupCode(), ""))
                            >= pool.minSwimmersInHeat())
                add(
                        out,
                        "SMALL_HEAT",
                        WARNING,
                        "В заплыве недостаточно участников",
                        discipline,
                        heat,
                        null);
            Set<Integer> lanes = new HashSet<>();
            for (SeededLane lane : heat.lanes()) {
                total++;
                if (lane.seedTimeMs() == null) nt++;
                if (!lanes.add(lane.lane()))
                    add(
                            out,
                            "LANE_CONFLICT",
                            ERROR,
                            "Две заявки на одной дорожке",
                            discipline,
                            heat,
                            lane);
                if (lane.lane() < 1 || lane.lane() > pool.lanes())
                    add(
                            out,
                            "LANE_OUT_OF_RANGE",
                            ERROR,
                            "Дорожка вне диапазона бассейна",
                            discipline,
                            heat,
                            lane);
                if (!athletes.add(lane.athleteId()))
                    add(
                            out,
                            "ATHLETE_IN_TWO_HEATS",
                            ERROR,
                            "Спортсмен назначен повторно в одном виде",
                            discipline,
                            heat,
                            lane);
                if (!registrations.add(lane.registrationId()))
                    add(
                            out,
                            "DUPLICATE_ENTRY",
                            ERROR,
                            "Заявка назначена повторно",
                            discipline,
                            heat,
                            lane);
                SeedEntry entry = byRegistration.get(lane.registrationId());
                if (entry == null
                        || entry.athleteId() == null
                        || entry.birthYear() == null
                        || entry.gender() == null
                        || !Objects.equals(entry.athleteId(), lane.athleteId())) {
                    add(
                            out,
                            "ATHLETE_NOT_FOUND",
                            ERROR,
                            "Спортсмен не найден среди допущенных заявок",
                            discipline,
                            heat,
                            lane);
                    continue;
                }
                if (!"X".equals(discipline.gender()) && !discipline.gender().equals(entry.gender()))
                    add(
                            out,
                            "GENDER_MISMATCH",
                            ERROR,
                            "Пол спортсмена не соответствует виду",
                            discipline,
                            heat,
                            lane);
                AgeGroupSpec group =
                        discipline.ageGroups().stream()
                                .filter(
                                        g ->
                                                ("X".equals(g.gender())
                                                                || g.gender()
                                                                        .equals(entry.gender()))
                                                        && (g.birthYearFrom() == null
                                                                || entry.birthYear()
                                                                        >= g.birthYearFrom())
                                                        && (g.birthYearTo() == null
                                                                || entry.birthYear()
                                                                        <= g.birthYearTo()))
                                .findFirst()
                                .orElse(null);
                if (!discipline.ageGroups().isEmpty()
                        && (group == null
                                || ("BY_AGE_GROUP".equals(discipline.seedingMode())
                                        && !Objects.equals(group.code(), heat.ageGroupCode()))))
                    add(
                            out,
                            "AGE_GROUP_MISMATCH",
                            ERROR,
                            "Возрастная группа не соответствует спортсмену",
                            discipline,
                            heat,
                            lane);
            }
        }
        if (nt * 2 > total)
            out.add(
                    new Violation(
                            "MANY_NT",
                            WARNING,
                            "Более половины участников без заявочного времени",
                            discipline.id(),
                            null,
                            null,
                            null));
        Map<UUID, List<ScheduledHeat>> starts = new HashMap<>();
        for (ScheduledHeat heat : schedule)
            if (heat.scheduledAt() != null)
                for (UUID athlete : new HashSet<>(heat.athleteIds()))
                    starts.computeIfAbsent(athlete, k -> new ArrayList<>()).add(heat);
        for (var entry : starts.entrySet()) {
            List<ScheduledHeat> list =
                    entry.getValue().stream()
                            .sorted(Comparator.comparing(ScheduledHeat::scheduledAt))
                            .toList();
            for (int i = 1; i < list.size(); i++) {
                ScheduledHeat a = list.get(i - 1), b = list.get(i);
                if ((a.disciplineId().equals(discipline.id())
                                || b.disciplineId().equals(discipline.id()))
                        && Duration.between(a.scheduledAt(), b.scheduledAt()).getSeconds()
                                < rules.minRestBetweenStartsSec())
                    out.add(
                            new Violation(
                                    "INSUFFICIENT_REST",
                                    WARNING,
                                    "Недостаточно отдыха между стартами",
                                    discipline.id(),
                                    b.disciplineId().equals(discipline.id())
                                            ? b.number()
                                            : a.number(),
                                    entry.getKey(),
                                    null));
            }
        }
        return List.copyOf(out);
    }

    private void add(
            List<Violation> out,
            String code,
            Violation.Severity severity,
            String message,
            DisciplineSpec discipline,
            SeededHeat heat,
            SeededLane lane) {
        out.add(
                new Violation(
                        code,
                        severity,
                        message,
                        discipline.id(),
                        heat.number(),
                        lane == null ? null : lane.athleteId(),
                        lane == null ? null : lane.registrationId()));
    }
}
