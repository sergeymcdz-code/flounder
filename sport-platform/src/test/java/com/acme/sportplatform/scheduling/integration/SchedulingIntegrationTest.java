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
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithUserDetails;
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
    @Autowired ApplicationContext context;

    @Test
    void schedulingKeepsApplicationAsyncExecutorSeparate() {
        var executor = assertInstanceOf(ThreadPoolTaskExecutor.class,
                context.getBean("applicationTaskExecutor"));
        var scheduler = assertInstanceOf(ThreadPoolTaskScheduler.class,
                context.getBean("taskScheduler"));
        assertNotSame(executor, scheduler);
        assertEquals(2, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
        assertEquals("app-scheduler-", scheduler.getThreadNamePrefix());
    }

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
    @WithUserDetails("admin@flounder.demo")
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
                .andExpect(status().isOk());
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
    @WithUserDetails("admin@flounder.demo")
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
    @WithUserDetails("admin@flounder.demo")
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
                .andExpect(status().isOk());
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
        AuthenticatedUserPrincipal user =
                new AuthenticatedUserPrincipal() {
                    public UUID getUserId() {
                        return userId;
                    }

                    public java.util.Collection<
                                    ? extends org.springframework.security.core.GrantedAuthority>
                            getAuthorities() {
                        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                };
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
    @WithUserDetails("admin@flounder.demo")
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
    @WithUserDetails("admin@flounder.demo")
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
                                .value(
                                        "Есть заплывы с результатами или уже проводившиеся заплывы,"
                                            + " пересоздание невозможно"));
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
    @WithUserDetails("admin@flounder.demo")
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
    @WithUserDetails("admin@flounder.demo")
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
    @WithUserDetails("admin@flounder.demo")
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

    @Test
    @WithUserDetails("admin@flounder.demo")
    void qa01PublishedWithdrawalWarnsWithoutMovingOtherLanes() throws Exception {
        // QA01-04: the validator must retain the published-change warning after withdrawal.
        var f = fixture(9);
        UUID discipline = f.disciplines().getFirst();
        List<UUID> withdrawn = f.registrations().subList(0, 2);
        ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        ok(post("/api/v1/event-disciplines/{id}/competition-units/publish", discipline));
        String before = lanes(discipline);
        Map<UUID, Integer> withdrawnLanes = new HashMap<>();
        for (UUID registration : withdrawn) {
            withdrawnLanes.put(registration, jdbc.queryForObject(
                    "select lane_or_position from competition_unit_entries where registration_id=?", Integer.class, registration));
            ok(post("/api/v1/registrations/{id}/withdraw", registration));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("WITHDRAWN",
                    jdbc.queryForObject("select entry_status from competition_unit_entries where registration_id=?", String.class, registration)));
        }
        var recalculated = ok(post("/api/v1/events/{id}/schedule/recalculate", f.event()));
        assertWithdrawalWarnings(recalculated, discipline, withdrawnLanes);
        var validation = ok(get("/api/v1/event-disciplines/{id}/competition-units/validate", discipline));
        assertWithdrawalWarnings(validation, discipline, withdrawnLanes);
        validation.path("violations").forEach(v -> assertNotEquals("ERROR", v.path("severity").asText()));
        assertEquals(before, lanes(discipline));
        assertTrue(jdbc.queryForObject("select exists(select 1 from rule_execution_logs where event_discipline_id=? and kind='VALIDATION' and violations_json @> '[{\"code\":\"PUBLISHED_HEAT_CHANGED\"}]'::jsonb)", Boolean.class, discipline));
        for (UUID registration : withdrawn)
            assertEquals(2, jdbc.queryForObject("select count(distinct kind) from rule_execution_logs where event_id=? and kind in ('SCHEDULE','VALIDATION') and violations_json @> ?::jsonb",
                    Integer.class, f.event(), "[{\"registrationId\":\"" + registration + "\"}]"));
        jdbc.update("update event_sessions set starts_at=starts_at+interval '1 minute' where event_id=?", f.event());
        var shifted = ok(post("/api/v1/events/{id}/schedule/recalculate", f.event()));
        var timeWarnings = StreamSupport.stream(shifted.path("violations").spliterator(), false)
                .filter(v -> "PUBLISHED_HEAT_CHANGED".equals(v.path("code").asText()) && !v.hasNonNull("registrationId")).toList();
        assertEquals(2, timeWarnings.size());
        timeWarnings.forEach(v -> assertEquals("Опубликованный заплыв изменён при пересчёте", v.path("message").asText()));
        assertEquals(2, StreamSupport.stream(shifted.path("violations").spliterator(), false)
                .filter(v -> v.hasNonNull("registrationId")).count());
    }

    void assertWithdrawalWarnings(JsonNode response, UUID discipline, Map<UUID, Integer> withdrawnLanes) {
        var warnings = StreamSupport.stream(response.path("violations").spliterator(), false)
                .filter(v -> "PUBLISHED_HEAT_CHANGED".equals(v.path("code").asText())).toList();
        assertEquals(withdrawnLanes.size(), warnings.size());
        assertEquals(withdrawnLanes.keySet(), warnings.stream()
                .map(v -> UUID.fromString(v.path("registrationId").asText())).collect(Collectors.toSet()));
        for (var warning : warnings) {
            UUID registration = UUID.fromString(warning.path("registrationId").asText());
            assertEquals("WARNING", warning.path("severity").asText());
            assertEquals(discipline.toString(), warning.path("eventDisciplineId").asText());
            assertEquals(2, warning.path("heatNumber").asInt());
            assertEquals("Опубликованный заплыв изменён: участник снят, дорожка " + withdrawnLanes.get(registration),
                    warning.path("message").asText());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 8, 9, 16, 17})
    @WithUserDetails("admin@flounder.demo")
    void qa01BoundaryCountsNtAndRepeatPublication(int count) throws Exception {
        var f = fixture(count);
        UUID discipline = f.disciplines().getFirst();
        jdbc.update("update registrations set seed_time_ms=null where event_discipline_id=?", discipline);
        var generated = ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        assertEquals(count, StreamSupport.stream(generated.path("heats").spliterator(), false)
                .mapToInt(h -> h.path("lanes").size()).sum());
        var validation = ok(get("/api/v1/event-disciplines/{id}/competition-units/validate", discipline));
        validation.path("violations").forEach(v -> assertNotEquals("ERROR", v.path("severity").asText()));
        assertEquals(count > 0, StreamSupport.stream(validation.path("violations").spliterator(), false)
                .anyMatch(v -> "MANY_NT".equals(v.path("code").asText())));
        if (count < 3) assertFalse(StreamSupport.stream(validation.path("violations").spliterator(), false)
                .anyMatch(v -> "SMALL_HEAT".equals(v.path("code").asText())));
        String before = lanes(discipline);
        ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        assertEquals(before, lanes(discipline));
        ok(post("/api/v1/event-disciplines/{id}/competition-units/publish", discipline));
        ok(post("/api/v1/event-disciplines/{id}/competition-units/publish", discipline));
        assertEquals(before, lanes(discipline));
    }

    @Test
    @WithUserDetails("admin@flounder.demo")
    void qa01ScheduleBreakEndTimeAndAgeBoundaryRest() throws Exception {
        var f = fixture(17, 9);
        UUID first = f.disciplines().getFirst(), second = f.disciplines().get(1);
        UUID session = jdbc.queryForObject("select session_id from event_disciplines where id=?", UUID.class, first);
        jdbc.update("update event_disciplines set session_id=?,order_in_session=2 where id=?", session, second);
        jdbc.update("update event_disciplines set seeding_mode='BY_AGE_GROUP',age_groups_json='[{\"code\":\"M-2014-2015\",\"name\":\"Юноши\",\"gender\":\"M\",\"birthYearFrom\":2014,\"birthYearTo\":2015}]'::jsonb where id=?", first);
        jdbc.update("update registrations set participant_snapshot=jsonb_set(participant_snapshot,'{birthDate}','\"2015-12-31\"'::jsonb) where id=?", f.registrations().get(1));
        UUID profile = jdbc.queryForObject("select participant_profile_id from registrations where id=?", UUID.class, f.registrations().getFirst());
        jdbc.update("update registrations set participant_profile_id=? where id=?", profile, f.registrations().get(17));
        var generated = ok(post("/api/v1/events/{id}/generate-competition-units", f.event()));
        generated.path("violations").forEach(v -> assertNotEquals("ERROR", v.path("severity").asText()));
        var units = ok(post("/api/v1/events/{id}/schedule/recalculate", f.event())).path("units");
        List<Instant> times = StreamSupport.stream(units.spliterator(), false).map(u -> Instant.parse(u.path("scheduledAt").asText())).toList();
        Instant start = Instant.parse("2026-10-17T07:00:00Z");
        assertEquals(List.of(start, start.plusSeconds(90), start.plusSeconds(180), start.plusSeconds(330), start.plusSeconds(480)), times);
        assertEquals(List.of(1, 2, 3, 4, 5), StreamSupport.stream(units.spliterator(), false).map(u -> u.path("sequenceInSession").asInt()).toList());
        var summary = ok(get("/api/v1/events/{id}/sessions", f.event())).get(0);
        assertEquals(5, summary.path("heatsCount").asInt());
        assertEquals(start.plusSeconds(630), Instant.parse(summary.path("estimatedEndsAt").asText()));
        assertTrue(StreamSupport.stream(ok(get("/api/v1/event-disciplines/{id}/competition-units/validate", first)).path("violations").spliterator(), false)
                .anyMatch(v -> "INSUFFICIENT_REST".equals(v.path("code").asText())));
        jdbc.update("update event_sessions set starts_at=starts_at+interval '10 minutes' where id=?", session);
        var changed = ok(post("/api/v1/events/{id}/schedule/recalculate", f.event())).path("units");
        assertEquals(start.plusSeconds(600), Instant.parse(changed.get(0).path("scheduledAt").asText()));
    }

    @Test
    @WithUserDetails("admin@flounder.demo")
    void qa01DraftWithdrawalAndAddedEntryRebuildAllHeats() throws Exception {
        var f = fixture(9);
        UUID discipline = f.disciplines().getFirst(), changed = f.registrations().getFirst();
        ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        var previousIds = jdbc.queryForList("select id from competition_units where event_discipline_id=?", UUID.class, discipline);
        ok(post("/api/v1/registrations/{id}/withdraw", changed));
        ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        assertEquals(1, jdbc.queryForObject("select count(*) from competition_units where event_discipline_id=?", Integer.class, discipline));
        assertTrue(Collections.disjoint(previousIds, jdbc.queryForList("select id from competition_units where event_discipline_id=?", UUID.class, discipline)));
        assertEquals(8, jdbc.queryForObject("select count(*) from competition_unit_entries e join competition_units u on u.id=e.competition_unit_id where u.event_discipline_id=?", Integer.class, discipline));
        UUID added = UUID.randomUUID(), profile = UUID.randomUUID();
        jdbc.update("insert into profiles(id,first_name,last_name,birth_date,gender) values(?,'Новый','Участник','2014-01-01','M')", profile);
        jdbc.update("insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,seed_time_ms,created_at,updated_at) select ?,event_id,event_discipline_id,?,'CONFIRMED',participant_snapshot,29000,now(),now() from registrations where id=?", added, profile, changed);
        var generated = ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        assertEquals(2, generated.path("heats").size());
        var addedLane = StreamSupport.stream(generated.path("heats").get(1).path("lanes").spliterator(), false)
                .filter(l -> added.toString().equals(l.path("registrationId").asText())).findFirst().orElseThrow();
        assertEquals(4, addedLane.path("lane").asInt());
        assertEquals(0, jdbc.queryForObject("select count(*) from competition_unit_entries where registration_id=?", Integer.class, changed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LANE_CONFLICT", "LANE_OUT_OF_RANGE", "ATHLETE_IN_TWO_HEATS", "HEAT_OVER_CAPACITY", "HEAT_NUMBER_SEQUENCE", "ATHLETE_NOT_FOUND", "GENDER_MISMATCH", "AGE_GROUP_MISMATCH"})
    @WithUserDetails("admin@flounder.demo")
    void qa01ValidatorDetectsCorruptedPersistedHeats(String code) throws Exception {
        var f = fixture(9);
        UUID discipline = f.disciplines().getFirst();
        ok(post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline));
        UUID first = jdbc.queryForObject("select id from competition_units where event_discipline_id=? and sequence_number=1", UUID.class, discipline);
        UUID second = jdbc.queryForObject("select id from competition_units where event_discipline_id=? and sequence_number=2", UUID.class, discipline);
        UUID entry = jdbc.queryForList("select id from competition_unit_entries where competition_unit_id=? order by lane_or_position", UUID.class, first).getFirst();
        UUID registration = jdbc.queryForObject("select registration_id from competition_unit_entries where id=?", UUID.class, entry);
        switch (code) {
            case "LANE_CONFLICT" -> jdbc.update("update competition_unit_entries set lane_or_position=4 where competition_unit_id=?", first);
            case "LANE_OUT_OF_RANGE" -> jdbc.update("update competition_unit_entries set lane_or_position=9 where id=?", entry);
            case "ATHLETE_IN_TWO_HEATS" -> jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position,seed_time_ms,entry_status) select ?,?,registration_id,1,seed_time_ms,'ASSIGNED' from competition_unit_entries where id=?", UUID.randomUUID(), second, entry);
            case "HEAT_OVER_CAPACITY" -> jdbc.update("update competition_unit_entries set competition_unit_id=? where competition_unit_id=?", second, first);
            case "HEAT_NUMBER_SEQUENCE" -> jdbc.update("update competition_units set sequence_number=3 where id=?", second);
            case "ATHLETE_NOT_FOUND" -> jdbc.update("update registrations set participant_snapshot=participant_snapshot-'birthDate' where id=?", registration);
            case "GENDER_MISMATCH" -> jdbc.update("update registrations set participant_snapshot=jsonb_set(participant_snapshot,'{gender}','\"F\"'::jsonb) where id=?", registration);
            case "AGE_GROUP_MISMATCH" -> jdbc.update("update event_disciplines set age_groups_json='[{\"code\":\"M-2015\",\"name\":\"Юноши\",\"gender\":\"M\",\"birthYearFrom\":2015,\"birthYearTo\":2015}]'::jsonb where id=?", discipline);
            default -> fail("Unknown corruption " + code);
        }
        var validation = ok(get("/api/v1/event-disciplines/{id}/competition-units/validate", discipline));
        assertTrue(StreamSupport.stream(validation.path("violations").spliterator(), false)
                .anyMatch(v -> code.equals(v.path("code").asText()) && "ERROR".equals(v.path("severity").asText())), validation.toString());
        mvc.perform(post("/api/v1/event-disciplines/{id}/competition-units/publish", discipline))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("scheduling.validation_failed"));
        assertEquals(0, jdbc.queryForObject("select count(*) from competition_units where event_discipline_id=? and status='PUBLISHED'", Integer.class, discipline));
        assertEquals(2, jdbc.queryForObject("select count(*) from rule_execution_logs where event_discipline_id=? and kind='VALIDATION'", Integer.class, discipline));
    }

    @Test
    @WithUserDetails("admin@flounder.demo")
    void qa01ConcurrentEventGenerationHasNoDuplicateHeats() throws Exception {
        var f = fixture(17, 9);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<JsonNode> generate = () -> {
                ready.countDown();
                assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                return ok(post("/api/v1/events/{id}/generate-competition-units", f.event()).with(principal(OWNER, "PLATFORM_ADMIN")));
            };
            var a = executor.submit(generate);
            var b = executor.submit(generate);
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            assertEquals(2, a.get(30, java.util.concurrent.TimeUnit.SECONDS).path("disciplines").size());
            assertEquals(2, b.get(30, java.util.concurrent.TimeUnit.SECONDS).path("disciplines").size());
        }
        assertEquals(5, jdbc.queryForObject("select count(*) from competition_units u join event_disciplines d on d.id=u.event_discipline_id where d.event_id=?", Integer.class, f.event()));
        assertEquals(26, jdbc.queryForObject("select count(*) from competition_unit_entries e join competition_units u on u.id=e.competition_unit_id join event_disciplines d on d.id=u.event_discipline_id where d.event_id=?", Integer.class, f.event()));
        assertEquals(0, jdbc.queryForObject("select count(*) from (select u.event_discipline_id,u.sequence_number from competition_units u join event_disciplines d on d.id=u.event_discipline_id where d.event_id=? group by u.event_discipline_id,u.sequence_number having count(*)>1) duplicates", Integer.class, f.event()));
        for (UUID discipline : f.disciplines()) ok(get("/api/v1/event-disciplines/{id}/competition-units/validate", discipline)).path("violations")
                .forEach(v -> assertNotEquals("ERROR", v.path("severity").asText()));
    }

    @Test
    void organizationMemberCanManageEventThroughT01AccessPolicy() throws Exception {
        var f = fixture(3);
        UUID organization =
                jdbc.queryForObject(
                        "select organization_id from events where id=?", UUID.class, f.event());
        UUID member = UUID.randomUUID();
        jdbc.update(
                "insert into organizations(id,type,name,created_at,updated_at)"
                    + " values(?,'ORGANIZER','Member scheduling test',now(),now())",
                organization);
        jdbc.update(
                "insert into users(id,email,password_hash,status)"
                    + " values(?,?,'test-unused','active')",
                member,
                member + "@example.test");
        jdbc.update(
                "insert into"
                    + " organization_members(id,organization_id,user_id,member_role,created_at)"
                    + " values(?,?,?,'OPERATOR',now())",
                UUID.randomUUID(),
                organization,
                member);
        UUID discipline = f.disciplines().getFirst();
        ok(
                post("/api/v1/event-disciplines/{id}/generate-competition-units", discipline)
                        .with(principal(member, "OPERATOR")));
        ok(
                post("/api/v1/event-disciplines/{id}/competition-units/publish", discipline)
                        .with(principal(member, "OPERATOR")));
        mvc.perform(
                        post(
                                        "/api/v1/event-disciplines/{id}/generate-competition-units",
                                        discipline)
                                .with(principal(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("events.access_denied"));
        mvc.perform(
                        post("/api/v1/registrations/{id}/withdraw", f.registrations().getFirst())
                                .with(principal(member, "OPERATOR")))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () ->
                                assertEquals(
                                        "WITHDRAWN",
                                        jdbc.queryForObject(
                                                "select entry_status from competition_unit_entries"
                                                    + " where registration_id=?",
                                                String.class,
                                                f.registrations().getFirst())));
    }
}
