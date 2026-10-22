package com.acme.sportplatform.results;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Batched read-only projection. Ranking remains owned by the results module. */
public interface ResultsProtocolLookup {
    List<Heat> heats(List<UUID> disciplineIds);

    record Heat(UUID disciplineId, int number, String ageGroupCode, String status,
            OffsetDateTime scheduledAt, List<Entry> entries) {}
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
