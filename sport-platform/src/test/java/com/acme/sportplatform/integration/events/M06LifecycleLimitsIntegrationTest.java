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

@org.springframework.test.context.event.RecordApplicationEvents
@AutoConfigureMockMvc
class M06LifecycleLimitsIntegrationTest extends AbstractPostgresIntegrationTest {
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

    @Autowired org.springframework.test.context.event.ApplicationEvents domainEvents;

    record Competition(Fixture owner, UUID event, UUID discipline, UUID other, String slug) {}
    Competition competition(Integer eventLimit, Integer disciplineLimit, Map<String, Integer> groups) throws Exception {
        var f = fixture(); UUID event = createEvent(f);
        if (eventLimit != null) call(patch("/api/v1/events/{id}", event), f.user(), "ORGANIZER", Map.of("maxParticipants", eventLimit), 200);
        var input = new HashMap<String, Object>(Map.of("disciplineTemplateId", f.template(), "distanceM", 50, "stroke", "FREE", "gender", "M"));
        input.put("ageGroups", List.of(Map.of("code", "young", "name", "Младшие", "gender", "M", "birthYearFrom", 2014, "birthYearTo", 2015),
                Map.of("code", "older", "name", "Старшие", "gender", "M", "birthYearFrom", 2012, "birthYearTo", 2013)));
        if (disciplineLimit != null) input.put("maxParticipants", disciplineLimit);
        if (groups != null) input.put("limitsByAgeGroup", groups);
        UUID one = UUID.fromString(call(post("/api/v1/events/{id}/disciplines", event), f.user(), "ORGANIZER", input, 200).path("id").asText());
        input.remove("maxParticipants"); input.remove("limitsByAgeGroup");
        UUID two = UUID.fromString(call(post("/api/v1/events/{id}/disciplines", event), f.user(), "ORGANIZER", input, 200).path("id").asText());
        for (var d : List.of(one, two)) call(post("/api/v1/events/{id}/disciplines/{d}/publish", event, d), f.user(), "ORGANIZER", null, 200);
        call(post("/api/v1/events/{id}/publish", event), f.user(), "ORGANIZER", null, 200);
        var opened = call(post("/api/v1/events/{id}/open-registration", event), f.user(), "ORGANIZER", null, 200);
        jdbc.update("update events set registration_open_at=null, registration_close_at=null where id=?", event);
        return new Competition(f, event, one, two, opened.path("publicSlug").asText());
    }
    UUID participant() {
        UUID user = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,status) values(?,?,'test-unused','active')", user, user + "@example.test");
        return user;
    }
    JsonNode submit(Competition c, UUID user, UUID discipline, int year) throws Exception {
        return call(post("/api/v1/public/events/{slug}/registrations", c.slug()), user, "PARTICIPANT",
                Map.of("profile", Map.of("lastName", "Тестов" + user, "firstName", "Иван", "birthDate", year + "-03-05", "gender", "M"),
                        "items", List.of(Map.of("eventDisciplineId", discipline, "seedTimeMs", 32150))), 200).get(0);
    }
    void confirm(Competition c, JsonNode registration) throws Exception {
        call(post("/api/v1/registrations/{id}/review", registration.path("id").asText()), c.owner().user(), "ORGANIZER", Map.of("decision", "CONFIRMED"), 200);
    }
    String persistedStatus(JsonNode registration) {
        return jdbc.queryForObject("select status from registrations where id=?", String.class, UUID.fromString(registration.path("id").asText()));
    }

    @Test
    void eventLimitCountsSwimmersOnceAcrossDisciplines() throws Exception {
        var c = competition(1, null, null); UUID user = participant();
        assertEquals("SUBMITTED", submit(c, user, c.discipline(), 2014).path("status").asText());
        assertEquals("SUBMITTED", submit(c, user, c.other(), 2014).path("status").asText());
        var waiting = submit(c, participant(), c.other(), 2014);
        assertEquals("WAITLISTED", waiting.path("status").asText()); assertEquals(1, waiting.path("waitlistPosition").asInt());
        var form = call(get("/api/v1/public/events/{slug}/registration-form", c.slug()), user, "PARTICIPANT", null, 200);
        assertEquals(1, form.path("participantsCount").asInt()); assertEquals(1, form.path("maxParticipants").asInt());
        assertEquals(1, domainEvents.stream(com.acme.sportplatform.registrations.RegistrationWaitlistedEvent.class).count());
    }

    @Test
    void disciplineLimitAndManualConfirmationOutsideLimit() throws Exception {
        var c = competition(null, 1, null);
        submit(c, participant(), c.discipline(), 2014);
        var waiting = submit(c, participant(), c.discipline(), 2014);
        assertEquals("WAITLISTED", waiting.path("status").asText());
        assertEquals("SUBMITTED", submit(c, participant(), c.other(), 2014).path("status").asText());
        confirm(c, waiting); assertEquals("CONFIRMED", persistedStatus(waiting));
        assertNull(jdbc.queryForObject("select waitlist_position from registrations where id=?", Integer.class, UUID.fromString(waiting.path("id").asText())));
        var filter = call(get("/api/v1/registrations").param("eventId", c.event().toString()).param("status", "WAITLISTED"), c.owner().user(), "ORGANIZER", null, 200);
        assertTrue(filter.isEmpty());
    }

    @Test
    void ageGroupLimitDoesNotBlockOtherGroups() throws Exception {
        var c = competition(null, null, Map.of("young", 1));
        submit(c, participant(), c.discipline(), 2014);
        assertEquals("WAITLISTED", submit(c, participant(), c.discipline(), 2015).path("status").asText());
        assertEquals("SUBMITTED", submit(c, participant(), c.discipline(), 2012).path("status").asText());
        var form = call(get("/api/v1/public/events/{slug}/registration-form", c.slug()), c.owner().user(), "ORGANIZER", null, 200);
        var view = java.util.stream.StreamSupport.stream(form.path("disciplines").spliterator(), false)
                .filter(d -> d.path("id").asText().equals(c.discipline().toString())).findFirst().orElseThrow();
        assertEquals(1, view.path("participantsByAgeGroup").path("young").asInt());
    }

    @ParameterizedTest @ValueSource(strings = {"withdraw", "reject"})
    void releasingConfirmedPlacePromotesFirstWaitingEntry(String action) throws Exception {
        var c = competition(null, 1, null);
        var admitted = submit(c, participant(), c.discipline(), 2014); confirm(c, admitted);
        var first = submit(c, participant(), c.discipline(), 2014);
        var second = submit(c, participant(), c.discipline(), 2014);
        assertEquals(1, first.path("waitlistPosition").asInt()); assertEquals(2, second.path("waitlistPosition").asInt());
        if (action.equals("withdraw")) call(post("/api/v1/registrations/{id}/withdraw", admitted.path("id").asText()), c.owner().user(), "ORGANIZER", null, 200);
        else call(post("/api/v1/registrations/{id}/review", admitted.path("id").asText()), c.owner().user(), "ORGANIZER", Map.of("decision", "REJECTED", "reviewNote", "Отказ"), 200);
        assertEquals("SUBMITTED", persistedStatus(first)); assertEquals("WAITLISTED", persistedStatus(second));
        assertEquals(1, domainEvents.stream(com.acme.sportplatform.registrations.RegistrationWaitlistPromotedEvent.class).count());
    }

    @Test
    void closedFormRejectsSubmissionBeforeProfileChangesAndCanBeReopened() throws Exception {
        var c = competition(null, null, null); UUID user = participant();
        call(post("/api/v1/events/{id}/close-registration", c.event()), c.owner().user(), "ORGANIZER", null, 200);
        var form = call(get("/api/v1/public/events/{slug}/registration-form", c.slug()), user, "PARTICIPANT", null, 200);
        assertFalse(form.path("registrationOpen").asBoolean());
        var rejected = call(post("/api/v1/public/events/{slug}/registrations", c.slug()), user, "PARTICIPANT",
                Map.of("profile", Map.of("lastName", "Тестов", "firstName", "Иван", "birthDate", "2014-03-05", "gender", "M"),
                        "items", List.of(Map.of("eventDisciplineId", c.discipline(), "seedTimeMs", 32150))), 400);
        assertEquals("registrations.registration_closed", rejected.path("code").asText());
        assertEquals(0, jdbc.queryForObject("select count(*) from profiles where user_id=?", Integer.class, user));
        call(post("/api/v1/events/{id}/open-registration", c.event()), c.owner().user(), "ORGANIZER", null, 200);
        assertEquals("SUBMITTED", submit(c, user, c.discipline(), 2014).path("status").asText());
    }

    @Test
    void archivedEventRemainsPublicButRejectsOrganizerWrites() throws Exception {
        var c = competition(null, null, null);
        var invalid = call(post("/api/v1/events/{id}/archive", c.event()), c.owner().user(), "ORGANIZER", null, 400);
        assertEquals("events.invalid_transition", invalid.path("code").asText());
        call(post("/api/v1/events/{id}/close-registration", c.event()), c.owner().user(), "ORGANIZER", null, 200);
        call(post("/api/v1/events/{id}/complete", c.event()), c.owner().user(), "ORGANIZER", null, 200);
        call(post("/api/v1/events/{id}/archive", c.event()), c.owner().user(), "ORGANIZER", null, 200);
        var archived = call(get("/api/v1/events/{id}", c.event()), c.owner().user(), "ORGANIZER", null, 200);
        assertEquals("ARCHIVED", archived.path("status").asText());
        for (String path : List.of("", "/start-list", "/schedule", "/live", "/results", "/registration-form"))
            call(get("/api/v1/public/events/" + c.slug() + path), c.owner().user(), "ORGANIZER", null, 200);
        assertEquals("events.archived", call(patch("/api/v1/events/{id}", c.event()), c.owner().user(), "ORGANIZER", Map.of("maxParticipants", 100), 400).path("code").asText());
        assertEquals("events.archived", call(delete("/api/v1/event-disciplines/{id}", c.other()), c.owner().user(), "ORGANIZER", null, 400).path("code").asText());
        assertEquals("events.archived", call(post("/api/v1/events/{id}/sessions", c.event()), c.owner().user(), "ORGANIZER", Map.of("name", "День", "orderNo", 1, "startsAt", "2030-06-01T10:00:00Z"), 400).path("code").asText());
    }

    @Test
    void seedingIgnoresWaitlistedEntries() throws Exception {
        var c = competition(null, 1, null);
        var confirmed = submit(c, participant(), c.discipline(), 2014); confirm(c, confirmed);
        var waiting = submit(c, participant(), c.discipline(), 2014);
        var generated = call(post("/api/v1/event-disciplines/{id}/generate-competition-units", c.discipline()), c.owner().user(), "ORGANIZER", Map.of("force", false), 200);
        assertFalse(generated.path("heats").toString().contains(waiting.path("id").asText()));
        assertTrue(generated.path("heats").toString().contains(confirmed.path("id").asText()));
    }

    @Test
    void optionalLimitsCanBeClearedAndDatesKeepQa01Validation() throws Exception {
        var f = fixture(); UUID event = createEvent(f);
        call(patch("/api/v1/events/{id}", event), f.user(), "ORGANIZER", Map.of("maxParticipants", 2), 200);
        var cleared = call(patch("/api/v1/events/{id}", event), f.user(), "ORGANIZER", json.readTree("{\"maxParticipants\":null}"), 200);
        assertFalse(cleared.hasNonNull("maxParticipants"));
        assertNull(jdbc.queryForObject("select max_participants from events where id=?", Integer.class, event));
        call(patch("/api/v1/events/{id}", event), f.user(), "ORGANIZER", Map.of("maxParticipants", 0), 400);
        assertEquals("events.invalid_dates", call(patch("/api/v1/events/{id}", event), f.user(), "ORGANIZER", Map.of("registrationCloseAt", "2020-01-01T10:00:00Z"), 400).path("code").asText());
        var d = call(post("/api/v1/events/{id}/disciplines", event), f.user(), "ORGANIZER", Map.of("disciplineTemplateId", f.template(), "distanceM", 50, "stroke", "FREE", "gender", "M", "maxParticipants", 5), 200);
        assertEquals(5, d.path("maxParticipants").asInt());
        var patched = call(patch("/api/v1/event-disciplines/{id}", d.path("id").asText()), f.user(), "ORGANIZER", json.readTree("{\"maxParticipants\":null}"), 200);
        assertFalse(patched.hasNonNull("maxParticipants"));
        assertNull(jdbc.queryForObject("select max_participants from event_disciplines where id=?", Integer.class, UUID.fromString(d.path("id").asText())));
        call(patch("/api/v1/event-disciplines/{id}", d.path("id").asText()), f.user(), "ORGANIZER", Map.of("limitsByAgeGroup", Map.of("missing", 1)), 400);
    }
    @Test
    void concurrentSubmissionsAcrossDisciplinesCannotExceedEventLimit() throws Exception {
        var c = competition(1, null, null); UUID firstUser = participant(), secondUser = participant();
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { gate.await(); return submit(c, firstUser, c.discipline(), 2014); });
            var second = pool.submit(() -> { gate.await(); return submit(c, secondUser, c.other(), 2014); });
            gate.countDown();
            var statuses = List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS).path("status").asText(),
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS).path("status").asText());
            assertEquals(1, statuses.stream().filter("SUBMITTED"::equals).count());
            assertEquals(1, statuses.stream().filter("WAITLISTED"::equals).count());
        }
    }

    @Test
    void resubmissionReturnsToWaitlistWhenFreedPlaceHasAlreadyBeenTaken() throws Exception {
        var c = competition(null, 1, null); UUID user = participant();
        var original = submit(c, user, c.discipline(), 2014);
        call(post("/api/v1/registrations/{id}/review", original.path("id").asText()), c.owner().user(), "ORGANIZER",
                Map.of("decision", "NEEDS_CORRECTION", "reviewNote", "Уточните данные"), 200);
        submit(c, participant(), c.discipline(), 2014);
        var resubmitted = call(post("/api/v1/registrations/{id}/resubmit", original.path("id").asText()), user, "PARTICIPANT", Map.of("seedTimeMs", 33000), 200);
        assertEquals("WAITLISTED", resubmitted.path("status").asText());
        assertEquals(1, resubmitted.path("waitlistPosition").asInt());
        // M05 x M06: only the initial submission emitted "submitted"; the waitlisted resubmission must not
        assertEquals(1, domainEvents.stream(com.acme.sportplatform.registrations.RegistrationSubmittedEvent.class)
                .filter(e -> e.registrationId().toString().equals(original.path("id").asText())).count());
    }

    @Test
    void eventPlaceIsFreedOnlyAfterSwimmersLastOccupyingEntryIsWithdrawn() throws Exception {
        var c = competition(1, null, null); UUID user = participant();
        var one = submit(c, user, c.discipline(), 2014);
        var two = submit(c, user, c.other(), 2014);
        var waiting = submit(c, participant(), c.other(), 2014);
        call(post("/api/v1/registrations/{id}/withdraw", one.path("id").asText()), c.owner().user(), "ORGANIZER", null, 200);
        assertEquals("WAITLISTED", persistedStatus(waiting));
        call(post("/api/v1/registrations/{id}/withdraw", two.path("id").asText()), c.owner().user(), "ORGANIZER", null, 200);
        assertEquals("SUBMITTED", persistedStatus(waiting));
    }

    @Autowired com.acme.sportplatform.events.EventLookup eventLookup;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void lockedLookupRefreshesEventReadEarlierInTheSameRequest() throws Exception {
        var c = competition(null, null, null);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            assertEquals("REGISTRATION_OPEN", eventLookup.getById(c.event()).status());
            jdbc.update("update events set status='REGISTRATION_CLOSED', max_participants=3 where id=?", c.event());
            var latest = eventLookup.lockById(c.event());
            assertEquals("REGISTRATION_CLOSED", latest.status());
            assertEquals(3, latest.maxParticipants());
        });
    }

    @Autowired com.acme.sportplatform.registrations.RegistrationImportLookup imports;
    @Autowired com.acme.sportplatform.identity.ProfileLookup profiles;

    @Test
    void organizerImportKeepsDemoSpecConfirmationContractOutsideLimits() throws Exception {
        var c = competition(1, 1, null);
        submit(c, participant(), c.discipline(), 2014);
        UUID importedUser = participant();
        assertEquals("WAITLISTED", submit(c, importedUser, c.other(), 2014).path("status").asText());
        var profile = profiles.getByUserId(importedUser);
        var row = new com.acme.sportplatform.identity.ProfileData(profile.lastName(), profile.firstName(),
                profile.middleName(), profile.birthDate(), profile.gender(), profile.clubName());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(transaction ->
                assertTrue(imports.upsert(c.event(), c.discipline(), profile.id(), row, 32150L, java.math.BigDecimal.ZERO)));
        assertEquals("CONFIRMED", jdbc.queryForObject("select status from registrations where event_discipline_id=? and participant_profile_id=?",
                String.class, c.discipline(), profile.id()));
    }


    @Autowired com.acme.sportplatform.events.EventLockService eventLocks;
    @Autowired javax.sql.DataSource dataSource;

    @Test
    void writableEventMutexAllowsAuditForeignKeyInsert() throws Exception {
        var c = competition(null, null, null);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        var holder = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
            eventLocks.lockWritableForUpdate(c.event());
            locked.countDown();
            try { assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }));
        try {
            assertTrue(locked.await(5, java.util.concurrent.TimeUnit.SECONDS));
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (var timeout = connection.createStatement()) { timeout.execute("set local lock_timeout = '1s'"); }
                try (var insert = connection.prepareStatement("insert into rule_execution_logs(id,event_id,kind,input_json,output_json,duration_ms) values(?,?,'VALIDATION','{}','{}',0)")) {
                    insert.setObject(1, UUID.randomUUID()); insert.setObject(2, c.event());
                    assertEquals(1, insert.executeUpdate());
                }
                connection.commit();
            }
        } finally { release.countDown(); holder.get(5, java.util.concurrent.TimeUnit.SECONDS); pool.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(strings = {"event", "discipline", "legacy", "group", "closed"})
    void increasingLimitsPromotesOnlyFirstWaitingEntry(String kind) throws Exception {
        boolean event = kind.equals("event");
        boolean group = kind.equals("group");
        var c = competition(event ? 1 : null, event || group ? null : 1, group ? Map.of("young", 1) : null);
        submit(c, participant(), c.discipline(), 2014);
        var first = submit(c, participant(), c.discipline(), 2014);
        var second = submit(c, participant(), c.discipline(), 2014);
        assertEquals("WAITLISTED", persistedStatus(first));
        if (kind.equals("closed")) call(post("/api/v1/events/{id}/close-registration", c.event()), c.owner().user(), "ORGANIZER", null, 200);
        if (kind.equals("legacy")) {
            call(patch("/api/v1/event-disciplines/{id}", c.discipline()), c.owner().user(), "ORGANIZER", Map.of("participantLimit", 1), 200);
            call(patch("/api/v1/event-disciplines/{id}", c.discipline()), c.owner().user(), "ORGANIZER", json.readTree("{\"maxParticipants\":null,\"participantLimit\":2}"), 200);
        } else {
            var body = group ? Map.of("limitsByAgeGroup", Map.of("young", 2)) : Map.of("maxParticipants", 2);
            call(patch(event ? "/api/v1/events/{id}" : "/api/v1/event-disciplines/{id}", event ? c.event() : c.discipline()), c.owner().user(), "ORGANIZER", body, 200);
        }
        assertEquals("SUBMITTED", persistedStatus(first));
        assertEquals("WAITLISTED", persistedStatus(second));
        assertEquals(1, domainEvents.stream(com.acme.sportplatform.registrations.RegistrationSubmittedEvent.class)
                .filter(e -> e.registrationId().toString().equals(first.path("id").asText())).count());
        call(patch("/api/v1/event-disciplines/{id}", c.discipline()), c.owner().user(), "ORGANIZER", Map.of("name", "Изменение"), 400);
        call(patch("/api/v1/event-disciplines/{id}", c.discipline()), c.owner().user(), "ORGANIZER", json.readTree("{\"name\":null,\"maxParticipants\":3}"), 400);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void malformedSnapshotDoesNotBreakPublicGroupCounts(boolean groupLimits) throws Exception {
        var c = competition(null, null, groupLimits ? Map.of("young", 2) : null);
        var entry = submit(c, participant(), c.discipline(), 2014);
        jdbc.update("update registrations set participant_snapshot='{}'::jsonb where id=?", UUID.fromString(entry.path("id").asText()));
        mvc.perform(get("/api/v1/public/events/{slug}/registration-form", c.slug())).andExpect(status().isOk());
    }

    @Test
    void lifecycleQueryOnlySelectsReachedDeadlines() throws Exception {
        var c = competition(null, null, null);
        var now = java.time.OffsetDateTime.parse("2030-06-01T10:00:00Z");
        var repository = lifecycleRepository;
        jdbc.update("update events set status='PUBLISHED', registration_open_at=?, registration_close_at=? where id=?", now.plusMinutes(1), now.plusMinutes(2), c.event());
        assertTrue(repository.findDueLifecycleEvents(now).stream().noneMatch(e -> e.getId().equals(c.event())));
        assertTrue(repository.findDueLifecycleEvents(now.plusMinutes(1)).stream().anyMatch(e -> e.getId().equals(c.event())));
        jdbc.update("update events set status='REGISTRATION_OPEN' where id=?", c.event());
        assertTrue(repository.findDueLifecycleEvents(now.plusMinutes(1)).stream().noneMatch(e -> e.getId().equals(c.event())));
        assertTrue(repository.findDueLifecycleEvents(now.plusMinutes(2)).stream().anyMatch(e -> e.getId().equals(c.event())));
        jdbc.update("update events set status='REGISTRATION_CLOSED' where id=?", c.event());
        assertTrue(repository.findDueLifecycleEvents(now.plusMinutes(3)).stream().noneMatch(e -> e.getId().equals(c.event())));
    }
    @Autowired com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository lifecycleRepository;
}
