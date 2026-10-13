package com.acme.sportplatform.results;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Isolated HTTP fixtures: FINISHED represents public results independently of M10 publication. */
@AutoConfigureMockMvc
@Transactional
abstract class Qa02ResultsFixture extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired @Qualifier("publicationCacheManager") CacheManager caches;
    UUID event, session, secondSession, discipline, template, owner, first, second;
    String slug;
    OffsetDateTime start;

    record Principal(UUID userId, List<GrantedAuthority> authorities) implements AuthenticatedUserPrincipal {
        @Override public UUID getUserId() { return userId; }
        @Override public List<GrantedAuthority> getAuthorities() { return authorities; }
    }

    @BeforeEach
    void fixture() {
        clearCaches();
        event = UUID.randomUUID(); session = UUID.randomUUID(); secondSession = UUID.randomUUID();
        discipline = UUID.randomUUID(); template = UUID.randomUUID(); owner = UUID.randomUUID();
        slug = "qa02-" + event;
        start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);
        UUID venue = UUID.randomUUID();
        jdbc.update("insert into venues(id,name,city,timezone,venue_meta) values (?,'Бассейн','Самара','Europe/Samara','{\"lanes\":8,\"courseLength\":25}')", venue);
        jdbc.update("""
                insert into events(id,organization_id,venue_id,sport_id,regulation_version_id,title,
                    event_start_at,event_end_at,status,public_slug,created_by_user_id,created_at,updated_at)
                values (?,?,?,'10000000-0000-0000-0000-000000000001',?,'QA02 Кубок',?,?,'REGISTRATION_OPEN',?,?,now(),now())
                """, event, UUID.randomUUID(), venue, UUID.randomUUID(), start, start.plusHours(6), slug, owner);
        jdbc.update("""
                insert into discipline_templates(id,sport_id,code,name,competition_format,unit_type,result_type,ranking_strategy)
                values (?,'10000000-0000-0000-0000-000000000001',?,'Плавание','INDIVIDUAL','HEAT','TIME','ASC')
                """, template, template.toString());
        jdbc.update("insert into event_sessions(id,event_id,name,order_no,starts_at) values (?,?,'Утро',1,?),(?,?,'День',2,?)",
                session, event, start, secondSession, event, start.plusHours(3));
        addDiscipline(discipline, session, 1);
        first = heat(discipline, 1, start);
        second = heat(discipline, 2, start.plusSeconds(90));
    }

    void addDiscipline(UUID id, UUID sessionId, int order) {
        jdbc.update("""
                insert into event_disciplines(id,event_id,discipline_template_id,code,name,competition_format,
                    unit_type,result_type,ranking_strategy,status,distance_m,stroke,gender,session_id,
                    order_in_session,age_groups_json,seeding_mode,created_at,updated_at)
                values (?,?,?,?,'50 м','INDIVIDUAL','HEAT','TIME','ASC','PUBLISHED',50,'FREE','X',?,?,
                    '[{"code":"YOUNG","name":"2014–2015","gender":"X","birthYearFrom":2014,"birthYearTo":2015},
                      {"code":"OLDER","name":"2012–2013","gender":"X","birthYearFrom":2012,"birthYearTo":2013}]','BY_TIME_ALL',now(),now())
                """, id, event, template, id.toString(), sessionId, order);
    }

    UUID heat(UUID disciplineId, int number, OffsetDateTime time) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status,scheduled_at,heat_duration_sec) values (?,?,'Заплыв',?,'PUBLISHED',?,90)",
                id, disciplineId, number, time);
        return id;
    }

    UUID entry(UUID unit, int lane, int year) throws Exception {
        UUID profile = UUID.randomUUID(), registration = UUID.randomUUID(), id = UUID.randomUUID();
        String lastName = "Иванов" + lane;
        String snapshot = json.writeValueAsString(Map.of("lastName", lastName, "firstName", "Иван",
                "birthDate", year + "-03-05", "gender", "M", "clubName", "Волна",
                "email", "private@qa02.example", "phone", "+79990000000"));
        jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) values (?,?,'Иван',?::date,'M','Волна')",
                profile, lastName, year + "-03-05");
        UUID disciplineId = jdbc.queryForObject("select event_discipline_id from competition_units where id=?", UUID.class, unit);
        jdbc.update("""
                insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,source,created_at,updated_at)
                values (?,?,?,?,'CONFIRMED',?::jsonb,'IMPORT',now(),now())
                """, registration, event, disciplineId, profile, snapshot);
        jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position,entry_status,seed_time_ms) values (?,?,?,?,'ASSIGNED',48000)",
                id, unit, registration, lane);
        return id;
    }

    UsernamePasswordAuthenticationToken auth(String role) {
        var authorities = List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + role));
        return new UsernamePasswordAuthenticationToken(new Principal(owner, authorities), null, authorities);
    }

    Map<String, Object> timed(UUID id, String time) { return Map.of("competitionUnitEntryId", id, "time", time, "status", "VALID"); }
    Map<String, Object> terminal(UUID id, String code) { return Map.of("competitionUnitEntryId", id, "status", code); }
    JsonNode save(UUID unit, String role, List<?> body, int expected) throws Exception {
        var response = mvc.perform(post("/api/v1/competition-units/{id}/results", unit)
                .with(authentication(auth(role))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }
    JsonNode read(String suffix) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/public/events/" + slug + suffix))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode result(UUID entry) {
        em.flush();
        var row = json.createObjectNode();
        jdbc.queryForMap("select status,time_ms,final_place,place_in_age_group,raw_value from results where competition_unit_entry_id=?", entry)
                .forEach((key, value) -> { if (value == null) row.putNull(key); else row.set(key, json.valueToTree(value)); });
        return row;
    }
    void state(UUID unit, String state) {
        em.flush();
        jdbc.update("update competition_units set status=? where id=?", state, unit);
        em.clear();
        clearCaches();
    }
    void clearCaches() { caches.getCacheNames().forEach(n -> caches.getCache(n).clear()); }
    JsonNode liveAction(UUID unit, String action) throws Exception {
        var response = mvc.perform(post("/api/v1/competition-units/{id}/" + action, unit)
                .with(authentication(auth("JUDGE")))).andExpect(status().isOk()).andReturn().getResponse();
        clearCaches();
        return json.readTree(response.getContentAsString());
    }
}
