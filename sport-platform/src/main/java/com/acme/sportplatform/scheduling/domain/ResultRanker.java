package com.acme.sportplatform.scheduling.domain;

import java.util.*;

public final class ResultRanker {
    public record ResultEntry(
            UUID registrationId,
            UUID athleteId,
            int birthYear,
            String gender,
            Long timeMs,
            String status,
            String ageGroupCode) {}

    public record RankedResult(UUID registrationId, Integer finalPlace, Integer placeInAgeGroup) {}

    public List<RankedResult> rank(List<ResultEntry> entries, List<AgeGroupSpec> groups) {
        List<ResultEntry> valid =
                entries.stream()
                        .filter(e -> "VALID".equals(e.status()) && e.timeMs() != null)
                        .sorted(
                                Comparator.comparing(ResultEntry::timeMs)
                                        .thenComparing(e -> e.registrationId().toString()))
                        .toList();
        Map<UUID, Integer> overall = places(valid), agePlaces = new HashMap<>();
        Map<String, List<ResultEntry>> grouped = new LinkedHashMap<>();
        for (ResultEntry entry : valid) {
            String code = entry.ageGroupCode();
            if (code == null)
                code =
                        groups.stream()
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
                                .map(AgeGroupSpec::code)
                                .findFirst()
                                .orElse(null);
            if (code != null) grouped.computeIfAbsent(code, k -> new ArrayList<>()).add(entry);
        }
        for (List<ResultEntry> group : grouped.values()) agePlaces.putAll(places(group));
        return entries.stream()
                .map(
                        e ->
                                new RankedResult(
                                        e.registrationId(),
                                        overall.get(e.registrationId()),
                                        agePlaces.get(e.registrationId())))
                .toList();
    }

    private Map<UUID, Integer> places(List<ResultEntry> sorted) {
        Map<UUID, Integer> out = new HashMap<>();
        Long previous = null;
        int place = 0;
        for (int i = 0; i < sorted.size(); i++) {
            ResultEntry entry = sorted.get(i);
            if (!entry.timeMs().equals(previous)) place = i + 1;
            out.put(entry.registrationId(), place);
            previous = entry.timeMs();
        }
        return out;
    }
}
