package com.acme.sportplatform.standings.infrastructure;

import com.acme.sportplatform.standings.domain.StandingsConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StandingsConfigRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public StandingsConfigRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public StandingsConfig get(UUID eventId) {
        return jdbc.query("select * from event_standings_configs where event_id=?", (rs, row) ->
                new StandingsConfig(rs.getBoolean("enabled"), StandingsConfig.Scope.valueOf(rs.getString("scope")),
                        read(rs.getString("points_by_place"), new TypeReference<List<Integer>>() {}),
                        (Integer) rs.getObject("max_scored_per_club_per_discipline"),
                        read(rs.getString("discipline_ids"), new TypeReference<List<UUID>>() {}),
                        rs.getBoolean("published"), rs.getString("title")), eventId).stream().findFirst()
                .orElseGet(StandingsConfig::defaults);
    }
    public void save(UUID eventId, StandingsConfig c) {
        jdbc.update("""
                insert into event_standings_configs(event_id,enabled,scope,points_by_place,
                    max_scored_per_club_per_discipline,discipline_ids,published,title)
                values (?,?,?,cast(? as jsonb),?,cast(? as jsonb),?,?)
                on conflict(event_id) do update set enabled=excluded.enabled,scope=excluded.scope,
                    points_by_place=excluded.points_by_place,
                    max_scored_per_club_per_discipline=excluded.max_scored_per_club_per_discipline,
                    discipline_ids=excluded.discipline_ids,published=excluded.published,title=excluded.title,updated_at=now()
                """, eventId, c.enabled(), c.scope().name(), write(c.pointsByPlace()),
                c.maxScoredPerClubPerDiscipline(), c.disciplineIds() == null ? null : write(c.disciplineIds()), c.published(), c.title());
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private <T> T read(String value, TypeReference<T> type) {
        if (value == null) return null;
        try { return json.readValue(value, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
}
