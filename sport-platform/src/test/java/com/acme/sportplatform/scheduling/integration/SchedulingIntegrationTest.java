package com.acme.sportplatform.scheduling.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.*;
import java.util.*;
import java.util.stream.*;

@AutoConfigureMockMvc
class SchedulingIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    record Fixture(UUID event, List<UUID> disciplines, List<UUID> registrations) {}

    Fixture fixture(int... counts) {
        UUID event = UUID.randomUUID(), venue = UUID.randomUUID(), template = UUID.randomUUID();
        jdbc.update(
                "insert into venues(id,name,timezone,venue_meta)"
                    + " values(?,'Pool','Europe/Moscow','{\"lanes\":8}'::jsonb)",
                venue);
        jdbc.update(
                """
                insert into events(id,created_by_user_id,organization_id,venue_id,sport_id,regulation_version_id,title,event_start_at,event_end_at,status,public_slug,created_at,updated_at)
                values(?,?,?,?,?,?,?,now(),now()+interval '1 day','DRAFT',?,now(),now())
                """,
                event,
                OWNER,
                UUID.randomUUID(),
                venue,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.randomUUID(),
                "Scheduling test",
                event.toString());
        jdbc.update(
                """
                insert into discipline_templates(id,sport_id,code,name,competition_format,unit_type,result_type,ranking_strategy)
                values(?,'10000000-0000-0000-0000-000000000001',?,'Freestyle','INDIVIDUAL','HEAT','TIME','ASC')
                """,
                template,
                template.toString());
        List<UUID> disciplines = new ArrayList<>(), registrations = new ArrayList<>();
        for (int d = 0; d < counts.length; d++) {
            UUID session = UUID.randomUUID(), discipline = UUID.randomUUID();
            disciplines.add(discipline);
            jdbc.update(
                    "insert into event_sessions(id,event_id,name,order_no,starts_at)"
                        + " values(?,?,'Session',?,?::timestamptz)",
                    session,
                    event,
                    d + 1,
                    "2026-10-17T" + (d == 0 ? "10" : "14") + ":00:00+03:00");
            jdbc.update(
                    """
                insert into event_disciplines(id,event_id,discipline_template_id,code,name,competition_format,unit_type,result_type,ranking_strategy,status,created_at,updated_at,
                distance_m,stroke,gender,seeding_mode,session_id,order_in_session)
                values(?,?,?,?,'Freestyle','INDIVIDUAL','HEAT','TIME','ASC','PUBLISHED',now(),now(),?,'FREE','M','BY_TIME_ALL',?,1)
                """,
                    discipline,
                    event,
                    template,
                    "d" + d,
                    d == 0 ? 50 : 100,
                    session);
            for (int i = 0; i < counts[d]; i++) {
                UUID profile = UUID.randomUUID(), registration = UUID.randomUUID();
                registrations.add(registration);
                jdbc.update(
                        "insert into profiles(id,first_name,last_name,birth_date,gender)"
                            + " values(?,'Имя',?,'2014-01-01','M')",
                        profile,
                        "Фам" + i);
                jdbc.update(
                        """
                insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,seed_time_ms,created_at,updated_at)
                values(?,?,?,?,?,?::jsonb,?,now(),now())
                """,
                        registration,
                        event,
                        discipline,
                        profile,
                        i % 3 == 0 ? "PAID" : i % 3 == 1 ? "ADMITTED" : "CONFIRMED",
                        "{\"lastName\":\"Фам"
                                + i
                                + "\",\"firstName\":\"Имя\",\"birthDate\":\"2014-01-01\",\"gender\":\"M\",\"clubName\":\"Клуб\"}",
                        (30L + i) * 1000);
            }
        }
        return new Fixture(event, disciplines, registrations);
    }

    JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    String lanes(UUID discipline) {
        return jdbc.queryForList(
                        """
                select u.sequence_number,e.lane_or_position,e.registration_id,e.seed_time_ms from competition_units u
                join competition_unit_entries e on e.competition_unit_id=u.id where u.event_discipline_id=? order by u.sequence_number,e.lane_or_position
                """,
                        discipline)
                .toString();
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void generateRepeatValidateSchedulePublishWithdraw() throws Exception {
        Fixture f = fixture(17, 20);
        var first = ok(post("/api/v1/events/{id}/generate-competition-units", f.event()));
        assertEquals(2, first.path("disciplines").size());
        for (UUID discipline : f.disciplines()) {
            assertEquals(
                    3,
                    jdbc.queryForObject(
                            "select count(*) from competition_units where event_discipline_id=?",
                            Integer.class,
                            discipline));
            var violations =
                    ok(get("/api/v1/event-disciplines/{id}/competition-units/validate", discipline))
                            .path("violations");
            violations.forEach(v -> assertNotEquals("ERROR", v.path("severity").asText()));
        }
        assertEquals(
                List.of(3, 6, 8),
                IntStream.range(0, 3)
                        .mapToObj(
                                i ->
                                        first.path("disciplines")
                                                .get(0)
                                                .path("result")
                                                .path("heats")
                                                .get(i)
                                                .path("lanes")
                                                .size())
                        .toList());
        var schedule =
                ok(post("/api/v1/events/{id}/schedule/recalculate", f.event())).path("units");
        assertEquals(6, schedule.size());
        Instant previous = Instant.MIN;
        for (JsonNode unit : schedule) {
            Instant time = Instant.parse(unit.path("scheduledAt").asText());
            assertTrue(time.isAfter(previous));
            previous = time;
        }
        assertEquals(1, schedule.get(0).path("sequenceInSession").asInt());
        assertEquals(1, schedule.get(3).path("sequenceInSession").asInt());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_id=? and"
                            + " kind='SEEDING'",
                        Integer.class,
                        f.event()));
        assertTrue(
                jdbc.queryForObject(
                        "select bool_and(duration_ms<5000 and"
                            + " jsonb_array_length(output_json->'heats')=3) from"
                            + " rule_execution_logs where event_id=? and kind='SEEDING'",
                        Boolean.class,
                        f.event()));
        String before = lanes(f.disciplines().getFirst());
        ok(
                post(
                                "/api/v1/event-disciplines/{id}/generate-competition-units",
                                f.disciplines().getFirst())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false,\"circleSeedTopHeats\":false}"));
        assertEquals(before, lanes(f.disciplines().getFirst()));
        ok(post("/api/v1/events/{id}/competition-units/publish", f.event()));
        assertEquals(
                6,
                jdbc.queryForObject(
                        "select count(*) from competition_units u join event_disciplines d on"
                            + " d.id=u.event_discipline_id where d.event_id=? and"
                            + " u.status='PUBLISHED'",
                        Integer.class,
                        f.event()));
        UUID withdrawn = f.registrations().getFirst();
        mvc.perform(
                        patch("/api/v1/registrations/{id}", withdrawn)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertEquals(
                                        "WITHDRAWN",
                                        jdbc.queryForObject(
                                                "select entry_status from competition_unit_entries"
                                                    + " where registration_id=?",
                                                String.class,
                                                withdrawn)));
        assertEquals(
                37,
                jdbc.queryForObject(
                        "select count(*) from competition_unit_entries e join competition_units u"
                            + " on u.id=e.competition_unit_id join event_disciplines d on"
                            + " d.id=u.event_discipline_id where d.event_id=?",
                        Integer.class,
                        f.event()));
        assertEquals(before, lanes(f.disciplines().getFirst()));
        ok(get(
                        "/api/v1/event-disciplines/{id}/competition-units/validate",
                        f.disciplines().getFirst()))
                .path("violations")
                .forEach(v -> assertNotEquals("ERROR", v.path("severity").asText()));
        mvc.perform(
                        post(
                                "/api/v1/event-disciplines/{id}/generate-competition-units",
                                f.disciplines().getFirst()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("scheduling.published_units_exist"));
        var forced =
                ok(
                        post(
                                        "/api/v1/event-disciplines/{id}/generate-competition-units",
                                        f.disciplines().getFirst())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"force\":true}"));
        assertTrue(
                StreamSupport.stream(forced.path("violations").spliterator(), false)
                        .anyMatch(v -> v.path("code").asText().equals("PUBLISHED_HEAT_CHANGED")));
        assertEquals(
                16,
                jdbc.queryForObject(
                        "select count(*) from competition_unit_entries e join competition_units u"
                            + " on u.id=e.competition_unit_id where u.event_discipline_id=?",
                        Integer.class,
                        f.disciplines().getFirst()));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void manualLaneConflictAndValidationBlockPublication() throws Exception {
        var f = fixture(17);
        UUID discipline = f.disciplines().getFirst();
        ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        var entries =
                jdbc.queryForList(
                        "select e.id,e.lane_or_position from competition_unit_entries e join"
                            + " competition_units u on u.id=e.competition_unit_id where"
                            + " u.event_discipline_id=? and u.sequence_number=1 order by"
                            + " e.lane_or_position",
                        discipline);
        UUID entry = (UUID) entries.getFirst().get("id");
        int taken = (Integer) entries.get(1).get("lane_or_position");
        mvc.perform(
                        patch("/api/v1/competition-unit-entries/{id}", entry)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"laneOrPosition\":" + taken + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("results.lane_conflict"));
        mvc.perform(
                        patch("/api/v1/competition-unit-entries/{id}", entry)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"laneOrPosition\":1}"))
                .andExpect(status().isNoContent());
        jdbc.update(
                "update competition_unit_entries set lane_or_position=? where id=?", taken, entry);
        mvc.perform(post("/api/v1/event-disciplines/{id}/competition-units/publish", discipline))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("scheduling.validation_failed"))
                .andExpect(jsonPath("$.violations[0].code").value("LANE_CONFLICT"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from competition_units where event_discipline_id=? and"
                            + " status='PUBLISHED'",
                        Integer.class,
                        discipline));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_discipline_id=? and"
                            + " kind='VALIDATION'",
                        Integer.class,
                        discipline));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void noSessionAndPublishedScheduleChangeAndDraftWithdrawal() throws Exception {
        var f = fixture(9);
        UUID discipline = f.disciplines().getFirst();
        jdbc.update("update event_disciplines set session_id=null where id=?", discipline);
        var generated =
                ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        assertTrue(generated.path("heats").get(0).has("scheduledAt"));
        assertTrue(generated.path("heats").get(0).get("scheduledAt").isNull());
        assertEquals("NO_SESSION", generated.path("violations").get(0).path("code").asText());
        UUID withdrawn = f.registrations().getFirst();
        mvc.perform(
                        patch("/api/v1/registrations/{id}", withdrawn)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertEquals(
                                        0,
                                        jdbc.queryForObject(
                                                "select count(*) from competition_unit_entries"
                                                    + " where registration_id=?",
                                                Integer.class,
                                                withdrawn)));
        UUID session =
                jdbc.queryForObject(
                        "select id from event_sessions where event_id=?", UUID.class, f.event());
        jdbc.update(
                "update event_disciplines set session_id=?,heat_duration_sec=42 where id=?",
                session,
                discipline);
        var schedule =
                ok(post("/api/v1/events/{id}/schedule/recalculate", f.event())).path("units");
        assertEquals(
                42,
                Duration.between(
                                Instant.parse(schedule.get(0).path("scheduledAt").asText()),
                                Instant.parse(schedule.get(1).path("scheduledAt").asText()))
                        .getSeconds());
        ok(post("/api/v1/event-disciplines/{id}/competition-units/publish", discipline));
        jdbc.update(
                "update event_sessions set starts_at=starts_at+interval '1 minute' where id=?",
                session);
        var recalculated = ok(post("/api/v1/events/{id}/schedule/recalculate", f.event()));
        assertEquals(
                "PUBLISHED_HEAT_CHANGED",
                recalculated.path("violations").get(0).path("code").asText());
    }

    RequestPostProcessor principal(UUID userId) {
        AuthenticatedUserPrincipal user = () -> userId;
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        user, "", List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
    }

    @Test
    void accessChecksOwnerAndDeniesOtherOrganizer() throws Exception {
        var f = fixture(3);
        UUID discipline = f.disciplines().getFirst();
        UUID stranger = UUID.randomUUID();
        mvc.perform(
                        post(
                                        "/api/v1/event-disciplines/{id}/generate-competition-units",
                                        discipline)
                                .with(principal(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/events/{id}/schedule/recalculate", f.event())
                                .with(principal(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/event-disciplines/{id}/competition-units/validate", discipline)
                                .with(principal(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/v1/events/{id}/competition-units/publish", f.event())
                                .with(principal(stranger)))
                .andExpect(status().isForbidden());
        ok(
                post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline)
                        .with(principal(OWNER)));
        UUID entry =
                jdbc.queryForObject(
                        "select e.id from competition_unit_entries e join competition_units u on"
                            + " u.id=e.competition_unit_id where u.event_discipline_id=? limit 1",
                        UUID.class,
                        discipline);
        mvc.perform(
                        patch("/api/v1/competition-unit-entries/{id}", entry)
                                .with(principal(stranger))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"laneOrPosition\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/api/v1/registrations/{id}", f.registrations().getFirst())
                                .with(principal(stranger))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentsScheduling() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/v1/event-disciplines/{id}/generate-competition-units'].post")
                                .exists());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void eventOperationsAreAtomicAndDatabasePerformanceIsWithinBudget() throws Exception {
        var f = fixture(200, 200, 200, 200);
        var generated = ok(post("/api/v1/events/{id}/generate-competition-units", f.event()));
        assertTrue(generated.path("durationMs").asLong() < 5000);
        assertEquals(
                100,
                jdbc.queryForObject(
                        "select count(*) from competition_units u join event_disciplines d on"
                            + " d.id=u.event_discipline_id where d.event_id=?",
                        Integer.class,
                        f.event()));
        assertTrue(
                jdbc.queryForObject(
                        "select bool_and(duration_ms<5000) from rule_execution_logs where"
                            + " event_id=? and kind='SEEDING'",
                        Boolean.class,
                        f.event()));
        System.out.println(
                "Scheduling database performance: 200 entries x 4 disciplines = "
                        + generated.path("durationMs").asLong()
                        + " ms");
        UUID last = f.disciplines().getLast();
        jdbc.update(
                "update competition_unit_entries set lane_or_position=0 where"
                    + " competition_unit_id=(select id from competition_units where"
                    + " event_discipline_id=? and sequence_number=1)",
                last);
        mvc.perform(post("/api/v1/events/{id}/competition-units/publish", f.event()))
                .andExpect(status().isConflict());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "select count(*) from competition_units u join event_disciplines d on"
                            + " d.id=u.event_discipline_id where d.event_id=? and"
                            + " u.status='PUBLISHED'",
                        Integer.class,
                        f.event()));
        jdbc.update(
                "update competition_units set status='PUBLISHED' where event_discipline_id=?",
                last);
        var ids =
                jdbc.queryForList(
                        "select u.id from competition_units u join event_disciplines d on"
                            + " d.id=u.event_discipline_id where d.event_id=? order by u.id",
                        UUID.class,
                        f.event());
        int runs =
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_id=?",
                        Integer.class,
                        f.event());
        mvc.perform(post("/api/v1/events/{id}/generate-competition-units", f.event()))
                .andExpect(status().isConflict());
        assertEquals(
                ids,
                jdbc.queryForList(
                        "select u.id from competition_units u join event_disciplines d on"
                            + " d.id=u.event_discipline_id where d.event_id=? order by u.id",
                        UUID.class,
                        f.event()));
        assertEquals(
                runs,
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_id=?",
                        Integer.class,
                        f.event()));
    }
}
