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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        assertEquals(
                1,
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_id=? and"
                            + " kind='SCHEDULE'",
                        Integer.class,
                        f.event()));
        for (JsonNode item : first.path("disciplines")) {
            JsonNode result = item.path("result");
            var recorded =
                    json.readTree(
                            jdbc.queryForObject(
                                    "select output_json::text from rule_execution_logs where id=?",
                                    String.class,
                                    UUID.fromString(result.path("runId").asText())));
            assertEquals(result, recorded);
            for (JsonNode heat : result.path("heats")) {
                assertEquals(
                        jdbc.queryForObject(
                                        "select scheduled_at from competition_units where id=?",
                                        java.sql.Timestamp.class,
                                        UUID.fromString(heat.path("id").asText()))
                                .toInstant(),
                        Instant.parse(heat.path("scheduledAt").asText()));
            }
        }
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
        return principal(userId, "ORGANIZER");
    }

    RequestPostProcessor principal(UUID userId, String role) {
        AuthenticatedUserPrincipal user = () -> userId;
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        user, "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
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

    @Test
    void judgeMemberAndOwnerCannotGeneratePublishOrEditLanes() throws Exception {
        var f = fixture(3);
        UUID organization =
                jdbc.queryForObject(
                        "select organization_id from events where id=?", UUID.class, f.event());
        jdbc.update(
                "insert into organizations(id,type,name,created_at,updated_at)"
                    + " values(?,'ORGANIZER','Judge membership test',now(),now())",
                organization);
        jdbc.update(
                "insert into"
                    + " organization_members(id,organization_id,user_id,member_role,created_at)"
                    + " values(?,?,?,'JUDGE',now())",
                UUID.randomUUID(),
                organization,
                OWNER);
        UUID discipline = f.disciplines().getFirst();
        ok(
                post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline)
                        .with(principal(OWNER)));
        for (String path :
                List.of(
                        "/api/v1/event-disciplines/" + discipline + "/generate-competition-units",
                        "/api/v1/events/" + f.event() + "/generate-competition-units",
                        "/api/v1/event-disciplines/" + discipline + "/competition-units/publish",
                        "/api/v1/events/" + f.event() + "/competition-units/publish")) {
            mvc.perform(post(path).with(principal(OWNER, "JUDGE")))
                    .andExpect(status().isForbidden());
        }
        UUID entry =
                jdbc.queryForObject(
                        "select e.id from competition_unit_entries e join competition_units u on"
                            + " u.id=e.competition_unit_id where u.event_discipline_id=? limit 1",
                        UUID.class,
                        discipline);
        mvc.perform(
                        patch("/api/v1/competition-unit-entries/{id}", entry)
                                .with(principal(OWNER, "JUDGE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"laneOrPosition\":1}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "PUBLISHED"})
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void forceRegenerationPreservesRecordedResults(String unitStatus) throws Exception {
        var f = fixture(3);
        UUID discipline = f.disciplines().getFirst();
        ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        var units =
                jdbc.queryForList(
                        "select id from competition_units where event_discipline_id=?",
                        UUID.class,
                        discipline);
        UUID entry =
                jdbc.queryForObject(
                        "select e.id from competition_unit_entries e where competition_unit_id=?"
                            + " limit 1",
                        UUID.class,
                        units.getFirst());
        UUID result = UUID.randomUUID();
        jdbc.update(
                "insert into"
                    + " results(id,competition_unit_entry_id,raw_value,result_type,status,time_ms)"
                    + " values(?,?,'30.00','TIME','VALID',30000)",
                result,
                entry);
        jdbc.update(
                "update competition_units set status=? where event_discipline_id=?",
                unitStatus,
                discipline);
        mvc.perform(
                        post(
                                        "/api/v1/event-disciplines/{id}/generate-competition-units",
                                        discipline)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"force\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("scheduling.active_units_exist"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Есть заплывы с результатами, пересоздание невозможно"));
        assertEquals(
                30000L,
                jdbc.queryForObject("select time_ms from results where id=?", Long.class, result));
        assertEquals(
                units,
                jdbc.queryForList(
                        "select id from competition_units where event_discipline_id=?",
                        UUID.class,
                        discipline));
        mvc.perform(
                        post("/api/v1/events/{id}/generate-competition-units", f.event())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"force\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("scheduling.active_units_exist"));
        assertEquals(
                entry,
                jdbc.queryForObject(
                        "select competition_unit_entry_id from results where id=?",
                        UUID.class,
                        result));
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "COMPLETED", "FINISHED"})
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void forceRegenerationNeverRebuildsActiveOrFinishedUnits(String unitStatus) throws Exception {
        var f = fixture(3);
        UUID discipline = f.disciplines().getFirst();
        ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        UUID unit =
                jdbc.queryForObject(
                        "select id from competition_units where event_discipline_id=?",
                        UUID.class,
                        discipline);
        jdbc.update("update competition_units set status=? where id=?", unitStatus, unit);
        mvc.perform(
                        post(
                                        "/api/v1/event-disciplines/{id}/generate-competition-units",
                                        discipline)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"force\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("scheduling.active_units_exist"));
        assertEquals(
                unitStatus,
                jdbc.queryForObject(
                        "select status from competition_units where id=?", String.class, unit));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void eventSkipsEveryMissingSwimmingFieldAndKeepsExistingUnits() throws Exception {
        var f = fixture(3, 3, 3, 3);
        UUID configured = f.disciplines().getFirst();
        for (UUID discipline : f.disciplines())
            ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        var oldUnits = new HashMap<UUID, UUID>();
        List<String> fields = List.of("distance_m", "stroke", "gender");
        for (int i = 1; i < 4; i++) {
            UUID discipline = f.disciplines().get(i);
            oldUnits.put(
                    discipline,
                    jdbc.queryForObject(
                            "select id from competition_units where event_discipline_id=?",
                            UUID.class,
                            discipline));
            jdbc.update(
                    "update event_disciplines set " + fields.get(i - 1) + "=null where id=?",
                    discipline);
        }
        int scheduleRuns =
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_id=? and"
                            + " kind='SCHEDULE'",
                        Integer.class,
                        f.event());
        int seedRuns =
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_id=? and"
                            + " kind='SEEDING'",
                        Integer.class,
                        f.event());
        var response = ok(post("/api/v1/events/{id}/generate-competition-units", f.event()));
        assertEquals(4, response.path("disciplines").size());
        assertEquals(3, response.path("violations").size());
        response.path("violations")
                .forEach(
                        v -> {
                            assertEquals("WARNING", v.path("severity").asText());
                            assertEquals("DISCIPLINE_NOT_CONFIGURED", v.path("code").asText());
                        });
        for (JsonNode item : response.path("disciplines")) {
            UUID discipline = UUID.fromString(item.path("eventDisciplineId").asText());
            if (discipline.equals(configured)) {
                assertEquals(1, item.path("result").path("heats").size());
                continue;
            }
            assertTrue(item.path("result").path("heats").isEmpty());
            assertEquals(
                    "DISCIPLINE_NOT_CONFIGURED",
                    item.path("result").path("violations").get(0).path("code").asText());
            assertEquals(
                    oldUnits.get(discipline),
                    jdbc.queryForObject(
                            "select id from competition_units where event_discipline_id=?",
                            UUID.class,
                            discipline));
            mvc.perform(
                            post(
                                    "/api/v1/event-disciplines/{id}/generate-competition-units",
                                    discipline))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("scheduling.discipline_not_configured"));
        }
        assertEquals(
                scheduleRuns + 1,
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_id=? and"
                            + " kind='SCHEDULE'",
                        Integer.class,
                        f.event()));
        assertEquals(
                seedRuns + 1,
                jdbc.queryForObject(
                        "select count(*) from rule_execution_logs where event_id=? and"
                            + " kind='SEEDING'",
                        Integer.class,
                        f.event()));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void cityFallbackAndVenueLaneLimit() throws Exception {
        var f = fixture(3);
        UUID discipline = f.disciplines().getFirst();
        jdbc.update(
                "update registrations set participant_snapshot=(participant_snapshot-'clubName') ||"
                    + " '{\"city\":\"Самара\"}'::jsonb where id=?",
                f.registrations().getFirst());
        var response =
                ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        var lanes = response.path("heats").get(0).path("lanes");
        var cityLane =
                StreamSupport.stream(lanes.spliterator(), false)
                        .filter(
                                l ->
                                        l.path("registrationId")
                                                .asText()
                                                .equals(f.registrations().getFirst().toString()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("Самара", cityLane.path("athlete").path("club").asText());
        UUID entry =
                jdbc.queryForObject(
                        "select id from competition_unit_entries where registration_id=?",
                        UUID.class,
                        f.registrations().getFirst());
        int oldLane =
                jdbc.queryForObject(
                        "select lane_or_position from competition_unit_entries where id=?",
                        Integer.class,
                        entry);
        mvc.perform(
                        patch("/api/v1/competition-unit-entries/{id}", entry)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"laneOrPosition\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("results.lane_out_of_range"))
                .andExpect(jsonPath("$.message").value("Дорожка вне диапазона бассейна"));
        assertEquals(
                oldLane,
                jdbc.queryForObject(
                        "select lane_or_position from competition_unit_entries where id=?",
                        Integer.class,
                        entry));
        mvc.perform(
                        patch("/api/v1/competition-unit-entries/{id}", entry)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"laneOrPosition\":8}"))
                .andExpect(status().isNoContent());
        jdbc.update(
                "update venues set venue_meta='{\"lanes\":6}'::jsonb where id=(select venue_id from"
                    + " events where id=?)",
                f.event());
        mvc.perform(
                        patch("/api/v1/competition-unit-entries/{id}", entry)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"laneOrPosition\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("results.lane_out_of_range"));
        mvc.perform(
                        patch("/api/v1/competition-unit-entries/{id}", entry)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"laneOrPosition\":6}"))
                .andExpect(status().isNoContent());
    }
}
