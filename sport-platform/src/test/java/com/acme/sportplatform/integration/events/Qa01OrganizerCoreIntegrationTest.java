package com.acme.sportplatform.integration.events;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class Qa01OrganizerCoreIntegrationTest extends AbstractPostgresIntegrationTest {
    static final UUID SPORT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID ADMIN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    RequestPostProcessor actor(UUID id, String role) {
        var principal = new PlatformUserPrincipal(id, id + "@example.test", "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return authentication(new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    JsonNode call(MockHttpServletRequestBuilder request, UUID actor, String role, Object body, int status) throws Exception {
        request.with(actor(actor, role));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse();
        return response.getContentAsString().isEmpty() ? json.nullNode() : json.readTree(response.getContentAsString());
    }

    record Fixture(UUID user, UUID organization, UUID venue, UUID regulation, UUID template) {}

    Fixture fixture() throws Exception {
        UUID user = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,status) values(?,?,'test-unused','active')", user, user + "@example.test");
        var request = call(post("/api/v1/organization-registration-requests"), user, "ORGANIZER",
                Map.of("organizationType", "organizer", "organizationName", "QA01 " + user), 201);
        call(post("/api/v1/organization-registration-requests/{id}/approve", request.path("id").asText()),
                ADMIN, "PLATFORM_ADMIN", null, 200);
        UUID organization = jdbc.queryForObject("select organization_id from organization_members where user_id=?", UUID.class, user);
        assertEquals("owner", jdbc.queryForObject("select member_role from organization_members where organization_id=? and user_id=?",
                String.class, organization, user));
        UUID venue = UUID.fromString(call(post("/api/v1/venues"), user, "ORGANIZER",
                Map.of("name", "QA01", "timezone", "Europe/Moscow", "venueMeta", Map.of("lanes", 8, "courseLength", 25)), 201).path("id").asText());
        UUID regulationTemplate = UUID.randomUUID(), regulation = UUID.randomUUID(), template = UUID.randomUUID();
        jdbc.update("insert into regulation_templates(id,sport_id,code,name,created_at) values(?,?,?,'QA01',now())", regulationTemplate, SPORT, regulationTemplate.toString());
        jdbc.update("insert into regulation_versions(id,regulation_template_id,version_no,status,rules_json,created_at) values(?,?,1,'published','{}'::jsonb,now())", regulation, regulationTemplate);
        jdbc.update("insert into discipline_templates(id,sport_id,code,name,competition_format,unit_type,result_type,ranking_strategy) values(?,?,?,'QA01','INDIVIDUAL','HEAT','TIME','ASC')", template, SPORT, template.toString());
        return new Fixture(user, organization, venue, regulation, template);
    }

    Map<String, Object> eventBody(Fixture f) {
        return new HashMap<>(Map.of("organizationId", f.organization(), "venueId", f.venue(), "sportId", SPORT,
                "regulationVersionId", f.regulation(), "title", "QA01", "eventStartAt", "2026-10-17T10:00:00+03:00",
                "eventEndAt", "2026-10-17T18:00:00+03:00", "registrationOpenAt", "2026-10-13T10:00:00+03:00",
                "registrationCloseAt", "2026-10-16T18:00:00+03:00"));
    }

    UUID createEvent(Fixture f) throws Exception {
        return UUID.fromString(call(post("/api/v1/events"), f.user(), "ORGANIZER", eventBody(f), 201).path("id").asText());
    }

    @Test
    void organizerCreatesCoreWithoutPlatformAdminAndCannotManageForeignEvent() throws Exception {
        var f = fixture();
        call(post("/api/v1/organizations"), f.user(), "ORGANIZER", Map.of("type", "organizer", "name", "QA01"), 403);
        UUID event = createEvent(f);
        var session = call(post("/api/v1/events/{id}/sessions", event), f.user(), "ORGANIZER",
                Map.of("name", "Утро", "orderNo", 1, "startsAt", "2026-10-17T10:00:00+03:00", "breakBetweenEventsSec", 60), 201);
        var body = new HashMap<String, Object>(Map.of("disciplineTemplateId", f.template(), "distanceM", 50,
                "stroke", "FREE", "gender", "M", "sessionId", session.path("id").asText(), "orderInSession", 1,
                "ageGroups", List.of(Map.of("code", "M-2014-2015", "name", "Юноши", "gender", "M", "birthYearFrom", 2014, "birthYearTo", 2015))));
        var discipline = call(post("/api/v1/events/{id}/disciplines", event), f.user(), "ORGANIZER", body, 200);
        assertEquals("50 м вольный стиль, юноши", discipline.path("name").asText());
        assertEquals("BY_AGE_GROUP", discipline.path("seedingMode").asText());
        UUID otherEvent = createEvent(f);
        var mismatch = call(post("/api/v1/events/{id}/disciplines", otherEvent), f.user(), "ORGANIZER", body, 400);
        assertEquals("events.session_event_mismatch", mismatch.path("code").asText());
        assertEquals("Сессия принадлежит другому событию", mismatch.path("message").asText());
        call(get("/api/v1/events/{id}", event), UUID.randomUUID(), "ORGANIZER", null, 403);
        call(delete("/api/v1/event-sessions/{id}", session.path("id").asText()), f.user(), "ORGANIZER", null, 409);
    }

    @ParameterizedTest
    @CsvSource({"6,25", "6,50", "8,25", "8,50", "10,25", "10,50"})
    void supportedPoolMetadataRoundTrips(int lanes, int length) throws Exception {
        UUID user = UUID.randomUUID();
        var venue = call(post("/api/v1/venues"), user, "ORGANIZER",
                Map.of("name", "Бассейн", "timezone", "Europe/Moscow", "venueMeta", Map.of("lanes", lanes, "courseLength", length)), 201);
        var loaded = call(get("/api/v1/venues/{id}", venue.path("id").asText()), user, "ORGANIZER", null, 200);
        assertEquals(venue.path("venueMeta"), loaded.path("venueMeta"));
        assertEquals(lanes, loaded.path("venueMeta").path("lanes").asInt());
        assertEquals(length, loaded.path("venueMeta").path("courseLength").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"lanes\":7}", "{\"lanes\":0}", "{\"lanes\":8.5}", "{\"lanes\":\"8\"}",
            "{\"courseLength\":30}", "{\"courseLength\":25.5}"})
    void rejectsInvalidPoolMetadata(String meta) throws Exception {
        // QA01-01: invalid pool settings must not be persisted.
        var error = call(post("/api/v1/venues"), UUID.randomUUID(), "ORGANIZER",
                Map.of("name", "Бассейн", "timezone", "Europe/Moscow", "venueMeta", json.readTree(meta)), 400);
        assertEquals("organizations.invalid_venue_meta", error.path("code").asText());
        assertEquals(json.readTree(meta).has("lanes") ? "Число дорожек должно быть 6, 8 или 10"
                : "Длина бассейна должна быть 25 или 50 м", error.path("message").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"lanes\":null}", "{\"courseLength\":null}",
            "{\"lanes\":null,\"courseLength\":50}", "{\"lanes\":8,\"courseLength\":null}"})
    void nullPoolMetadataIsTreatedAsUnspecified(String meta) throws Exception {
        UUID user = UUID.randomUUID();
        var venue = call(post("/api/v1/venues"), user, "ORGANIZER",
                Map.of("name", "Бассейн", "timezone", "Europe/Moscow", "venueMeta", json.readTree(meta)), 201);
        var loaded = call(get("/api/v1/venues/{id}", venue.path("id").asText()), user, "ORGANIZER", null, 200);
        for (String field : List.of("lanes", "courseLength")) {
            var expected = json.readTree(meta).path(field);
            if (expected.isNumber()) assertEquals(expected, loaded.path("venueMeta").path(field));
            else assertTrue(loaded.path("venueMeta").path(field).isMissingNode()
                    || loaded.path("venueMeta").path(field).isNull());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventEndAt", "registrationCloseAt"})
    void rejectsReversedDateIntervals(String field) throws Exception {
        // QA01-02: valid ISO dates still need interval validation.
        var f = fixture();
        var body = eventBody(f);
        body.put(field, "2026-10-12T09:00:00+03:00");
        var error = call(post("/api/v1/events"), f.user(), "ORGANIZER", body, 400);
        assertEquals("events.invalid_dates", error.path("code").asText());
        assertEquals(field, error.path("violations").get(0).path("field").asText());
        assertEquals(0, jdbc.queryForObject("select count(*) from events where organization_id=?", Integer.class, f.organization()));
    }

    @Test
    void rejectsEmptyCoreFieldsAndMalformedDates() throws Exception {
        var f = fixture();
        var body = eventBody(f);
        body.put("title", " ");
        var emptyTitle = call(post("/api/v1/events"), f.user(), "ORGANIZER", body, 400);
        assertEquals("Укажите название события", emptyTitle.path("violations").get(0).path("message").asText());
        body.put("title", "QA01");
        body.put("eventStartAt", "2026-02-30T10:00:00+03:00");
        call(post("/api/v1/events"), f.user(), "ORGANIZER", body, 400);
        UUID event = createEvent(f);
        call(post("/api/v1/events/{id}/sessions", event), f.user(), "ORGANIZER", Map.of(), 400);
        call(post("/api/v1/venues"), f.user(), "ORGANIZER", Map.of("name", "", "timezone", ""), 400);
        call(post("/api/v1/events/{id}/disciplines", event), f.user(), "ORGANIZER", Map.of(), 400);
    }

    @Test
    void eventAndDisciplineTransitionsRejectRepeatsWithRussianMessages() throws Exception {
        // QA01-03: business error messages are part of the Russian API contract.
        var f = fixture();
        UUID event = createEvent(f);
        var transitionMessages = Map.of(
                "publish", "Опубликовать можно только событие в статусе DRAFT",
                "open-registration", "Открыть регистрацию можно только для события в статусе PUBLISHED",
                "close-registration", "Закрыть регистрацию можно только для события в статусе REGISTRATION_OPEN",
                "complete", "Завершить можно только событие в статусе LIVE или REGISTRATION_CLOSED");
        for (String action : List.of("open-registration", "close-registration", "complete")) {
            var rejected = call(post("/api/v1/events/{id}/{action}", event, action), f.user(), "ORGANIZER", null, 400);
            assertEquals("events.invalid_status_transition", rejected.path("code").asText());
            assertEquals(transitionMessages.get(action), rejected.path("message").asText());
        }
        var discipline = call(post("/api/v1/events/{id}/disciplines", event), f.user(), "ORGANIZER",
                Map.of("disciplineTemplateId", f.template(), "distanceM", 50, "stroke", "FREE", "gender", "M"), 200);
        String disciplineId = discipline.path("id").asText();
        assertEquals("PUBLISHED", call(post("/api/v1/events/{id}/disciplines/{d}/publish", event, disciplineId), f.user(), "ORGANIZER", null, 200).path("status").asText());
        var repeated = call(post("/api/v1/events/{id}/disciplines/{d}/publish", event, disciplineId), f.user(), "ORGANIZER", null, 400);
        assertEquals("competition.discipline_not_draft", repeated.path("code").asText());
        assertEquals("Опубликовать можно только дисциплину в статусе DRAFT", repeated.path("message").asText());
        var foreign = call(post("/api/v1/events/{id}/disciplines/{d}/publish", createEvent(f), disciplineId), f.user(), "ORGANIZER", null, 400);
        assertEquals("competition.discipline_event_mismatch", foreign.path("code").asText());
        assertEquals("Дисциплина принадлежит другому событию", foreign.path("message").asText());
        String[] actions = {"publish", "open-registration", "close-registration", "complete"};
        String[] statuses = {"PUBLISHED", "REGISTRATION_OPEN", "REGISTRATION_CLOSED", "COMPLETED"};
        for (int i = 0; i < actions.length; i++) {
            assertEquals(statuses[i], call(post("/api/v1/events/{id}/{action}", event, actions[i]), f.user(), "ORGANIZER", null, 200).path("status").asText());
            var invalid = call(post("/api/v1/events/{id}/{action}", event, actions[i]), f.user(), "ORGANIZER", null, 400);
            assertEquals("events.invalid_status_transition", invalid.path("code").asText());
            assertEquals(transitionMessages.get(actions[i]), invalid.path("message").asText());
        }
    }
}
