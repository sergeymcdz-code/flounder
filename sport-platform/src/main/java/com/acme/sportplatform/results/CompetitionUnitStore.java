package com.acme.sportplatform.results;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public persistence boundary for generated competition units. */
public interface CompetitionUnitStore {
    record Entry(UUID id, int lane, UUID registrationId, Long seedTimeMs, String status) {}

    record Unit(
            UUID id,
            UUID disciplineId,
            int number,
            String ageGroupCode,
            String status,
            Integer heatDurationSec,
            Instant scheduledAt,
            List<Entry> entries) {
        public Unit {
            entries = List.copyOf(entries);
        }
    }

    List<Unit> findByDisciplineId(UUID disciplineId);

    void replace(UUID disciplineId, boolean force, List<Unit> units, UUID runId);

    void schedule(UUID unitId, Instant scheduledAt);

    void publishDrafts(UUID disciplineId);

    void withdraw(UUID registrationId);
}
