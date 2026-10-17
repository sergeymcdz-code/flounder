package com.acme.sportplatform.scheduling.infrastructure;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BibNumberRepository {
    private final JdbcTemplate jdbc;
    public BibNumberRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<UUID, Integer> byEvent(UUID eventId) {
        var out = new LinkedHashMap<UUID, Integer>();
        jdbc.query("select profile_id, bib_number from event_bib_numbers where event_id=? order by bib_number",
                rs -> { out.put(rs.getObject(1, UUID.class), rs.getInt(2)); }, eventId);
        return out;
    }
    public void clear(UUID eventId) { jdbc.update("delete from event_bib_numbers where event_id=?", eventId); }
    public void save(UUID eventId, UUID profileId, int bib) {
        jdbc.update("insert into event_bib_numbers(event_id,profile_id,bib_number) values (?,?,?) on conflict(event_id,profile_id) do update set bib_number=excluded.bib_number, assigned_at=now()", eventId, profileId, bib);
    }
}
