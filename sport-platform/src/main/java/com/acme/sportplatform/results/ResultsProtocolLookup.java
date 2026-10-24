package com.acme.sportplatform.results;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Batched read-only projection. Ranking remains owned by the results module. */
public interface ResultsProtocolLookup {
    List<Heat> heats(List<UUID> disciplineIds);

    default List<Heat> heats(List<UUID> disciplineIds, boolean publishedOnly) {
        return heats(disciplineIds).stream().filter(h -> !publishedOnly
                || java.util.Set.of("FINISHED", "COMPLETED").contains(h.status())).toList();
    }

    record Heat(UUID disciplineId, int number, String ageGroupCode, String status,
            OffsetDateTime scheduledAt, List<Entry> entries, boolean official) {
        public Heat(UUID disciplineId, int number, String ageGroupCode, String status,
                OffsetDateTime scheduledAt, List<Entry> entries) {
            this(disciplineId, number, ageGroupCode, status, scheduledAt, entries, false);
        }
    }
    record Entry(Integer lane, String lastName, String firstName, Integer birthYear, String gender,
            String club, Long seedTimeMs, boolean withdrawn, Outcome outcome, UUID profileId) {
        public Entry(Integer lane, String lastName, String firstName, Integer birthYear, String gender,
                String club, Long seedTimeMs, boolean withdrawn, Outcome outcome) {
            this(lane,lastName,firstName,birthYear,gender,club,seedTimeMs,withdrawn,outcome,null);
        }
    }
    record Outcome(Long timeMs, String status, String rawValue, Integer place,
            Integer placeInAgeGroup) {}
}
