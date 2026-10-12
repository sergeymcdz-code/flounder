package com.acme.sportplatform.results.infrastructure;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.CompetitionUnitStore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class GeneratedCompetitionUnitStore implements CompetitionUnitStore {
    private final JdbcTemplate jdbc;

    public GeneratedCompetitionUnitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Unit> findByDisciplineId(UUID id) {
        Map<UUID, List<Entry>> entries = new HashMap<>();
        jdbc.query(
                """
                select e.* from competition_unit_entries e join competition_units u on u.id=e.competition_unit_id
                where u.event_discipline_id=? order by e.lane_or_position, e.id
                """,
                rs -> {
                    UUID unit = rs.getObject("competition_unit_id", UUID.class);
                    entries.computeIfAbsent(unit, k -> new ArrayList<>())
                            .add(
                                    new Entry(
                                            rs.getObject("id", UUID.class),
                                            rs.getInt("lane_or_position"),
                                            rs.getObject("registration_id", UUID.class),
                                            rs.getObject("seed_time_ms", Long.class),
                                            rs.getString("entry_status")));
                },
                id);
        return jdbc.query(
                "select * from competition_units where event_discipline_id=? order by"
                    + " sequence_number,id",
                (rs, n) -> {
                    UUID unit = rs.getObject("id", UUID.class);
                    Timestamp time = rs.getTimestamp("scheduled_at");
                    return new Unit(
                            unit,
                            id,
                            rs.getInt("sequence_number"),
                            rs.getString("age_group_code"),
                            rs.getString("status"),
                            rs.getObject("heat_duration_sec", Integer.class),
                            time == null ? null : time.toInstant(),
                            entries.getOrDefault(unit, List.of()));
                },
                id);
    }

    public void replace(UUID disciplineId, boolean force, List<Unit> units, UUID runId) {
        List<Unit> existing = findByDisciplineId(disciplineId);
        if (!force && existing.stream().anyMatch(u -> "PUBLISHED".equals(u.status())))
            throw new BusinessException(
                    "scheduling.published_units_exist",
                    "Есть опубликованные заплывы. Для пересоздания укажите force=true");
        if (!force
                && existing.stream()
                        .anyMatch(u -> !Set.of("DRAFT", "PUBLISHED").contains(u.status())))
            throw new BusinessException(
                    "scheduling.active_units_exist",
                    "Есть заплывы, которые уже проводятся или завершены. Для пересоздания укажите"
                        + " force=true");
        jdbc.update(
                """
                delete from results where competition_unit_entry_id in
                (select e.id from competition_unit_entries e join competition_units u on u.id=e.competition_unit_id where u.event_discipline_id=?)
                """,
                disciplineId);
        jdbc.update(
                "delete from competition_unit_entries where competition_unit_id in (select id from"
                    + " competition_units where event_discipline_id=?)",
                disciplineId);
        jdbc.update("delete from competition_units where event_discipline_id=?", disciplineId);
        for (Unit unit : units) {
            jdbc.update(
                    """
                insert into competition_units(id,event_discipline_id,label,sequence_number,status,age_group_code,heat_duration_sec,generation_run_id)
                values(?,?,?,?,'DRAFT',?,?,?)
                """,
                    unit.id(),
                    disciplineId,
                    "Заплыв " + unit.number(),
                    unit.number(),
                    unit.ageGroupCode(),
                    unit.heatDurationSec(),
                    runId);
            for (Entry entry : unit.entries())
                jdbc.update(
                        """
                insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position,seed_time_ms,entry_status)
                values(?,?,?,?,?,'ASSIGNED')
                """,
                        entry.id(),
                        unit.id(),
                        entry.registrationId(),
                        entry.lane(),
                        entry.seedTimeMs());
        }
    }

    public void schedule(UUID unitId, Instant time) {
        jdbc.update(
                "update competition_units set scheduled_at=?, updated_at=now(), version=version+1"
                    + " where id=?",
                time == null ? null : Timestamp.from(time),
                unitId);
    }

    public void publishDrafts(UUID disciplineId) {
        jdbc.update(
                "update competition_units set status='PUBLISHED',updated_at=now(),version=version+1"
                    + " where event_discipline_id=? and status='DRAFT'",
                disciplineId);
    }

    public void withdraw(UUID registrationId) {
        jdbc.update(
                """
                update competition_unit_entries set entry_status='WITHDRAWN' where registration_id=? and competition_unit_id in
                (select id from competition_units where status='PUBLISHED')
                """,
                registrationId);
        jdbc.update(
                """
                delete from competition_unit_entries where registration_id=? and competition_unit_id in
                (select id from competition_units where status='DRAFT')
                """,
                registrationId);
    }
}
