package com.acme.sportplatform.integration.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.sportplatform.competition.AgeGroup;
import com.acme.sportplatform.competition.infrastructure.jpa.*;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.*;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.*;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.registrations.RegistrationWithdrawn;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;
import com.acme.sportplatform.results.infrastructure.jpa.*;
import com.acme.sportplatform.sports.infrastructure.jpa.*;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.event.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@AutoConfigureMockMvc
@RecordApplicationEvents
class DemoDataModelIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final UUID SPORT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired ProfileLookup profileLookup;
    @Autowired EventRepository events;
    @Autowired DisciplineTemplateRepository templates;
    @Autowired EventDisciplineRepository disciplines;
    @Autowired RegistrationRepository registrations;
    @Autowired CompetitionUnitRepository units;
    @Autowired CompetitionUnitEntryRepository entries;
    @Autowired ResultRepository results;
    @Autowired ApplicationEvents applicationEvents;

    @Test
    void managesSessionsAndRejectsDeleteWhenReferenced() throws Exception {
        var owner = athlete("F");
        var event = event(owner.getUserId(), "PUBLISHED");
        var session =
                call(
                        post("/api/v1/events/{id}/sessions", event.getId()),
                        owner("ORGANIZER", owner),
                        Map.of(
                                "name",
                                "Утро",
                                "orderNo",
                                1,
                                "startsAt",
                                OffsetDateTime.now().plusDays(1).toString()),
                        201);
        String id = session.path("id").asText();
        assertEquals(60, session.path("breakBetweenEventsSec").asInt());
        call(
                get("/api/v1/events/{id}/sessions", event.getId()),
                owner("ORGANIZER", owner),
                null,
                200);
        call(
                patch("/api/v1/event-sessions/{id}", id),
                owner("OPERATOR", owner),
                Map.of("name", "Вечер", "breakBetweenEventsSec", 90),
                200);
        var d = discipline(event, "F", BigDecimal.ZERO);
        d.setSessionId(UUID.fromString(id));
        disciplines.saveAndFlush(d);
        var conflict =
                call(
                        delete("/api/v1/event-sessions/{id}", id),
                        owner("ORGANIZER", owner),
                        null,
                        409);
        assertEquals("events.session_in_use", conflict.path("code").asText());
        mvc.perform(get("/api/v1/public/events/{slug}", event.getPublicSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].name").value("Вечер"))
                .andExpect(jsonPath("$.disciplines[0].sessionId").value(id))
                .andExpect(jsonPath("$.disciplines[0].unitsPublished").value(false));
        call(
                delete("/api/v1/event-disciplines/{id}", d.getId()),
                owner("ORGANIZER", owner),
                null,
                204);
        call(delete("/api/v1/event-sessions/{id}", id), owner("ORGANIZER", owner), null, 204);
    }

    @Test
    void rejectsForeignEventManagementAndDuplicateSessionOrder() throws Exception {
        var a = athlete("F");
        var b = athlete("M");
        var e = event(a.getUserId(), "DRAFT");
        Map<String, Object> body =
                Map.of("name", "Утро", "orderNo", 1, "startsAt", OffsetDateTime.now().toString());
        call(post("/api/v1/events/{id}/sessions", e.getId()), owner("ORGANIZER", b), body, 403);
        call(post("/api/v1/events/{id}/sessions", e.getId()), owner("ORGANIZER", a), body, 201);
        assertEquals(
                "events.session_order_conflict",
                call(
                                post("/api/v1/events/{id}/sessions", e.getId()),
                                owner("ORGANIZER", a),
                                body,
                                409)
                        .path("code")
                        .asText());
    }

    @Test
    void createsGeneratedDisciplineCodesAndValidatesGroups() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "PUBLISHED");
        var template = template();
        Map<String, Object> body =
                new HashMap<>(
                        Map.of(
                                "disciplineTemplateId",
                                template.getId(),
                                "distanceM",
                                50,
                                "stroke",
                                "FREE",
                                "gender",
                                "F",
                                "ageGroups",
                                groups("F")));
        var d =
                call(
                        post("/api/v1/events/{id}/disciplines", e.getId()),
                        owner("ORGANIZER", a),
                        body,
                        200);
        assertEquals("F-50-FREE", d.path("code").asText());
        assertEquals("50 м вольный стиль, девушки", d.path("name").asText());
        var second =
                call(
                        post("/api/v1/events/{id}/disciplines", e.getId()),
                        owner("ORGANIZER", a),
                        body,
                        200);
        assertEquals("F-50-FREE-2", second.path("code").asText());
        call(
                patch("/api/v1/event-disciplines/{id}", d.path("id").asText()),
                owner("ORGANIZER", a),
                Map.of("heatDurationSec", 70),
                200);
        body.put("distanceM", 75);
        call(post("/api/v1/events/{id}/disciplines", e.getId()), owner("ORGANIZER", a), body, 400);
        body.put("distanceM", 50);
        body.put(
                "ageGroups",
                List.of(
                        new AgeGroup("a", "Группа", "F", 2014, 2015),
                        new AgeGroup("b", "Группа", "F", 2015, 2016)));
        call(post("/api/v1/events/{id}/disciplines", e.getId()), owner("ORGANIZER", a), body, 400);
        body.put("ageGroups", List.of(new AgeGroup("a", "Группа", "F", 2016, 2014)));
        call(post("/api/v1/events/{id}/disciplines", e.getId()), owner("ORGANIZER", a), body, 400);
        mvc.perform(get("/api/v1/age-group-presets").with(user(owner("ORGANIZER", a))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groups.length()").value(8));
    }

    @Test
    void publicFormCreatesProfileAndPaidRegistrationFlow() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", new BigDecimal("500"));
        mvc.perform(get("/api/v1/public/events/{slug}/registration-form", e.getPublicSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disciplines[0].entryFee").value(500));
        var submitted = submit(a, e, List.of(d));
        String id = submitted.get(0).path("id").asText();
        assertEquals("FORM", submitted.get(0).path("source").asText());
        assertEquals("PENDING", submitted.get(0).path("paymentStatus").asText());
        assertEquals(32150, submitted.get(0).path("seedTimeMs").asLong());
        assertEquals("00:32.15", submitted.get(0).path("seedTime").asText());
        assertEquals(
                "Новый клуб", profiles.findByUserId(a.getUserId()).orElseThrow().getClubName());
        call(
                post("/api/v1/registrations/{id}/review", id),
                owner("ORGANIZER", a),
                Map.of("decision", "CONFIRMED"),
                200);
        var paid =
                call(
                        post("/api/v1/registrations/{id}/mark-paid", id),
                        owner("OPERATOR", a),
                        null,
                        200);
        assertEquals("PAID", paid.path("status").asText());
        assertEquals("PAID", paid.path("paymentStatus").asText());
        call(delete("/api/v1/event-disciplines/{id}", d.getId()), owner("ORGANIZER", a), null, 409);
    }

    @Test
    void rejectsGenderMismatchAndRollsBackWholePublicBatch() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var good = discipline(e, "F", BigDecimal.ZERO);
        var bad = discipline(e, "M", BigDecimal.ZERO);
        var error =
                call(
                        post("/api/v1/public/events/{slug}/registrations", e.getPublicSlug()),
                        a,
                        form(a, List.of(good, bad)),
                        400);
        assertEquals("registrations.athlete_not_eligible", error.path("code").asText());
        assertTrue(error.path("message").asText().contains(bad.getName()));
        assertTrue(registrations.findByEventId(e.getId()).isEmpty());
        assertEquals(
                "Исходный клуб", profiles.findByUserId(a.getUserId()).orElseThrow().getClubName());
    }

    @Test
    void rejectsClosedRegistrationByStatusAndDeadline() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_CLOSED");
        var d = discipline(e, "F", BigDecimal.ZERO);
        call(
                post("/api/v1/public/events/{slug}/registrations", e.getPublicSlug()),
                a,
                form(a, List.of(d)),
                400);
        e.setStatus("REGISTRATION_OPEN");
        e.setRegistrationCloseAt(OffsetDateTime.now().minusSeconds(1));
        events.saveAndFlush(e);
        call(
                post("/api/v1/public/events/{slug}/registrations", e.getPublicSlug()),
                a,
                form(a, List.of(d)),
                400);
        mvc.perform(
                        post("/api/v1/public/events/{slug}/registrations", e.getPublicSlug())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(form(a, List.of(d)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withdrawPublishesModulithEventAndEnforcesOwnership() throws Exception {
        var a = athlete("F");
        var b = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        String id = submit(a, e, List.of(d)).get(0).path("id").asText();
        call(post("/api/v1/registrations/{id}/withdraw", id), b, null, 403);
        var withdrawn = call(post("/api/v1/registrations/{id}/withdraw", id), a, null, 200);
        assertEquals("WITHDRAWN", withdrawn.path("status").asText());
        var event =
                applicationEvents.stream(RegistrationWithdrawn.class)
                        .filter(x -> x.registrationId().toString().equals(id))
                        .findFirst()
                        .orElseThrow();
        assertEquals(e.getId(), event.eventId());
        assertEquals(d.getId(), event.eventDisciplineId());
        call(post("/api/v1/registrations/{id}/resubmit", id), a, null, 400);
    }

    @Test
    void participantCannotWithdrawAfterClosingButOrganizerCan() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        String id = submit(a, e, List.of(d)).get(0).path("id").asText();
        e.setStatus("REGISTRATION_CLOSED");
        events.saveAndFlush(e);
        call(post("/api/v1/registrations/{id}/withdraw", id), a, null, 400);
        call(post("/api/v1/registrations/{id}/withdraw", id), owner("ORGANIZER", a), null, 200);
    }

    @Test
    void resubmitsNeedsCorrectionAndChecksStatusTransitions() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        String id = submit(a, e, List.of(d)).get(0).path("id").asText();
        call(
                patch("/api/v1/registrations/{id}", id),
                owner("ORGANIZER", a),
                Map.of("status", "ADMITTED"),
                400);
        call(
                post("/api/v1/registrations/{id}/review", id),
                owner("ORGANIZER", a),
                Map.of("decision", "NEEDS_CORRECTION", "reviewNote", "Уточните клуб"),
                200);
        assertEquals(
                "SUBMITTED",
                call(post("/api/v1/registrations/{id}/resubmit", id), a, null, 200)
                        .path("status")
                        .asText());
    }

    @Test
    void enforcesCapacityAndSupportsSeedTimeString() throws Exception {
        var a = athlete("F");
        var b = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        d.setParticipantLimit(1);
        disciplines.saveAndFlush(d);
        var r =
                call(
                        post("/api/v1/registrations"),
                        a,
                        Map.of("eventDisciplineId", d.getId(), "seedTime", "0:32.15"),
                        200);
        assertEquals(32150L, r.path("seedTimeMs").asLong());
        assertEquals("NOT_REQUIRED", r.path("paymentStatus").asText());
        var waiting = call(
                post("/api/v1/public/events/{slug}/registrations", e.getPublicSlug()),
                b,
                form(b, List.of(d)),
                200).get(0);
        assertEquals("WAITLISTED", waiting.path("status").asText());
        assertEquals(1, waiting.path("waitlistPosition").asInt());
        assertEquals(1L, registrations.countByEventDisciplineIdAndStatusIn(
                d.getId(), List.of("SUBMITTED", "UNDER_REVIEW", "CONFIRMED", "PAID", "ADMITTED")));
    }

    @Test
    void attachesImportedProfileWithoutAccountAndListsExistingRegistration() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        String key = "импорт|анна|2015-06-01|f";
        ProfileData data =
                new ProfileData("Импорт", "Анна", null, LocalDate.of(2015, 6, 1), "F", "Клуб");
        var profile = profileLookup.findOrCreateByExternalKey(key, data);
        assertNull(profile.userId());
        assertEquals(
                profile.id(),
                profileLookup.findOrCreateByExternalKey(key.toUpperCase(Locale.ROOT), data).id());
        var registration = registration(d, profile.id(), null);
        registrations.saveAndFlush(registration);
        UserEntity u = new UserEntity();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("test-hash");
        u.setStatus("active");
        u = users.saveAndFlush(u);
        profileLookup.attachProfileToUser(key, u.getId());
        var actor = principal(u.getId(), "PARTICIPANT");
        mvc.perform(get("/api/v1/registrations/me").with(user(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(registration.getId().toString()));
        assertThrows(
                com.acme.sportplatform.common.exception.BusinessException.class,
                () -> profileLookup.attachProfileToUser(key, a.getUserId()));
    }

    @Test
    void ranksEqualTimesWithGapsAndNonFinishesWithoutPlaces() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        var unit = unit(d, 1);
        List<UUID> entryIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var athlete = athlete("F");
            var profile = profiles.findByUserId(athlete.getUserId()).orElseThrow();
            var reg =
                    registrations.saveAndFlush(
                            registration(d, profile.getId(), athlete.getUserId()));
            var entry = new CompetitionUnitEntryEntity();
            entry.setId(UUID.randomUUID());
            entry.setCompetitionUnitId(unit.getId());
            entry.setRegistrationId(reg.getId());
            entry.setLaneOrPosition(i + 1);
            entry.setCreatedAt(OffsetDateTime.now());
            entryIds.add(entries.saveAndFlush(entry).getId());
        }
        List<Map<String, Object>> batch =
                List.of(
                        Map.of(
                                "competitionUnitEntryId",
                                entryIds.get(0),
                                "time",
                                "00:32.15",
                                "status",
                                "VALID"),
                        Map.of(
                                "competitionUnitEntryId",
                                entryIds.get(1),
                                "timeMs",
                                32150,
                                "status",
                                "VALID"),
                        Map.of(
                                "competitionUnitEntryId",
                                entryIds.get(2),
                                "timeMs",
                                33000,
                                "status",
                                "VALID"),
                        Map.of("competitionUnitEntryId", entryIds.get(3), "status", "DNS"));
        var table =
                call(
                        post("/api/v1/competition-units/{id}/results", unit.getId()),
                        owner("OPERATOR", a),
                        batch,
                        200);
        assertEquals(4, table.path("entries").size());
        assertEquals(
                1,
                results.findByCompetitionUnitEntryId(entryIds.get(0))
                        .orElseThrow()
                        .getFinalPlace());
        assertEquals(
                1,
                results.findByCompetitionUnitEntryId(entryIds.get(1))
                        .orElseThrow()
                        .getFinalPlace());
        assertEquals(
                3,
                results.findByCompetitionUnitEntryId(entryIds.get(2))
                        .orElseThrow()
                        .getFinalPlace());
        assertEquals(
                3,
                results.findByCompetitionUnitEntryId(entryIds.get(2))
                        .orElseThrow()
                        .getPlaceInAgeGroup());
        assertNull(
                results.findByCompetitionUnitEntryId(entryIds.get(3))
                        .orElseThrow()
                        .getFinalPlace());
        call(
                get("/api/v1/event-disciplines/{id}/results", d.getId()),
                owner("ORGANIZER", a),
                null,
                200);
        List<Map<String, Object>> changed =
                List.of(
                        Map.of("competitionUnitEntryId", entryIds.get(0), "status", "DNF"),
                        Map.of("competitionUnitEntryId", entryIds.get(1), "status", "DSQ"));
        call(
                post("/api/v1/competition-units/{id}/results", unit.getId()),
                owner("OPERATOR", a),
                changed,
                200);
        assertNull(
                results.findByCompetitionUnitEntryId(entryIds.get(0))
                        .orElseThrow()
                        .getFinalPlace());
        assertEquals(
                1,
                results.findByCompetitionUnitEntryId(entryIds.get(2))
                        .orElseThrow()
                        .getFinalPlace());
        assertEquals(
                "DID_NOT_FINISH",
                results.findByCompetitionUnitEntryId(entryIds.get(0)).orElseThrow().getStatus());
        assertNull(
                results.findByCompetitionUnitEntryId(entryIds.get(0)).orElseThrow().getRawValue());
        call(
                post("/api/v1/competition-units/{id}/results", unit.getId()),
                owner("OPERATOR", a),
                changed,
                200);
        assertEquals(0, ruleLogs.findByEventDisciplineId(d.getId()).size());
    }

    @Test
    void liveFinishMovesToNextScheduledHeatInSameEvent() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        var one = unit(d, 1);
        var two = unit(d, 2);
        var foreign = event(a.getUserId(), "REGISTRATION_OPEN");
        var other = unit(discipline(foreign, "F", BigDecimal.ZERO), 1);
        call(
                put("/api/v1/events/{id}/live", e.getId()),
                owner("OPERATOR", a),
                Map.of("currentUnitId", other.getId()),
                400);
        assertEquals(
                one.getId().toString(),
                call(
                                post("/api/v1/competition-units/{id}/start", one.getId()),
                                owner("OPERATOR", a),
                                null,
                                200)
                        .path("currentUnitId")
                        .asText());
        assertEquals(
                two.getId().toString(),
                call(
                                post("/api/v1/competition-units/{id}/finish", one.getId()),
                                owner("OPERATOR", a),
                                null,
                                200)
                        .path("currentUnitId")
                        .asText());
        assertEquals("FINISHED", units.findById(one.getId()).orElseThrow().getStatus());
        call(
                post("/api/v1/competition-units/{id}/start", two.getId()),
                owner("OPERATOR", a),
                null,
                200);
        assertTrue(
                call(
                                post("/api/v1/competition-units/{id}/finish", two.getId()),
                                owner("OPERATOR", a),
                                null,
                                200)
                        .path("currentUnitId")
                        .isNull());
    }

    @Test
    void servesUpdatedOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/events/{eventId}/sessions']").exists())
                .andExpect(
                        jsonPath("$.components.schemas.SwimResultRequest.properties.timeMs")
                                .exists());
    }

    @Autowired RuleExecutionLogRepository ruleLogs;

    @Test
    void sessionsIncludeHeatCountAndEstimatedEndAndAllowJudge() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "PUBLISHED");
        var start = OffsetDateTime.parse("2026-10-15T09:00:00+03:00");
        var session =
                call(
                        post("/api/v1/events/{id}/sessions", e.getId()),
                        owner("ORGANIZER", a),
                        Map.of("name", "Утро", "orderNo", 1, "startsAt", start.toString()),
                        201);
        call(
                post("/api/v1/events/{id}/sessions", e.getId()),
                owner("ORGANIZER", a),
                Map.of("name", "Вечер", "orderNo", 2, "startsAt", start.plusHours(1).toString()),
                201);
        var d = discipline(e, "F", BigDecimal.ZERO);
        d.setSessionId(UUID.fromString(session.path("id").asText()));
        d.setHeatDurationSec(70);
        disciplines.saveAndFlush(d);
        var one = unit(d, 1);
        one.setScheduledAt(start);
        one.setHeatDurationSec(40);
        units.saveAndFlush(one);
        var two = unit(d, 2);
        two.setScheduledAt(start.plusMinutes(1));
        two.setHeatDurationSec(null);
        units.saveAndFlush(two);
        var list =
                call(get("/api/v1/events/{id}/sessions", e.getId()), owner("JUDGE", a), null, 200);
        assertEquals(2, list.get(0).path("heatsCount").asInt());
        assertEquals(
                start.plusSeconds(130).toInstant(),
                OffsetDateTime.parse(list.get(0).path("estimatedEndsAt").asText()).toInstant());
        assertEquals(0, list.get(1).path("heatsCount").asInt());
        assertTrue(list.get(1).path("estimatedEndsAt").isNull());
    }

    @Test
    void recordsPublishedHeatThenCorrectsAndAuditsRanking() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        var u = unit(d, 1);
        u.setStatus("PUBLISHED");
        units.saveAndFlush(u);
        List<UUID> ids = new ArrayList<>();
        for (int lane = 1; lane <= 2; lane++) {
            var athlete = athlete("F");
            var profile = profiles.findByUserId(athlete.getUserId()).orElseThrow();
            var r =
                    registrations.saveAndFlush(
                            registration(d, profile.getId(), athlete.getUserId()));
            var entry = new CompetitionUnitEntryEntity();
            entry.setId(UUID.randomUUID());
            entry.setCompetitionUnitId(u.getId());
            entry.setRegistrationId(r.getId());
            entry.setLaneOrPosition(lane);
            entry.setCreatedAt(OffsetDateTime.now());
            ids.add(entries.saveAndFlush(entry).getId());
        }
        call(
                post("/api/v1/competition-units/{id}/results", u.getId()),
                owner("JUDGE", a),
                List.of(
                        Map.of(
                                "competitionUnitEntryId",
                                ids.get(0),
                                "timeMs",
                                32150,
                                "status",
                                "VALID"),
                        Map.of(
                                "competitionUnitEntryId",
                                ids.get(1),
                                "timeMs",
                                33000,
                                "status",
                                "VALID")),
                200);
        var correction =
                List.of(
                        Map.of(
                                "competitionUnitEntryId",
                                ids.get(1),
                                "timeMs",
                                31000,
                                "status",
                                "VALID"));
        // Repeated input before results publication is ordinary judge input.
        call(
                post("/api/v1/competition-units/{id}/results", u.getId()),
                owner("JUDGE", a),
                List.of(Map.of("competitionUnitEntryId", ids.get(1), "timeMs", 33100, "status", "VALID")),
                200);
        assertEquals("VALID", results.findByCompetitionUnitEntryId(ids.get(1)).orElseThrow().getStatus());
        assertEquals(0, ruleLogs.findByEventDisciplineId(d.getId()).size());
        call(post("/api/v1/competition-units/{id}/publication", u.getId()),
                owner("JUDGE", a), Map.of("published", true), 204);
        assertEquals("FINISHED", units.findById(u.getId()).orElseThrow().getStatus());
        call(
                post("/api/v1/competition-units/{id}/results", u.getId()),
                owner("JUDGE", a),
                correction,
                403);
        call(
                post("/api/v1/competition-units/{id}/results", u.getId()),
                owner("OPERATOR", a),
                correction,
                200);
        var result = results.findByCompetitionUnitEntryId(ids.get(1)).orElseThrow();
        assertEquals("CORRECTED", result.getStatus());
        assertEquals(1, result.getFinalPlace());
        assertEquals(1, result.getPlaceInAgeGroup());
        assertEquals(
                2, results.findByCompetitionUnitEntryId(ids.get(0)).orElseThrow().getFinalPlace());
        var logs = ruleLogs.findByEventDisciplineId(d.getId());
        assertEquals(1, logs.size());
        assertEquals("RANKING", logs.get(0).getKind());
        assertTrue(logs.get(0).getInputJson().toString().contains("33100"));
        assertTrue(logs.get(0).getOutputJson().toString().contains("CORRECTED"));
        var grouped =
                call(
                        get("/api/v1/event-disciplines/{id}/results", d.getId()),
                        owner("ORGANIZER", a),
                        null,
                        200);
        assertEquals("F-2014-2015", grouped.path("groups").get(0).path("ageGroupCode").asText());
        assertEquals(
                ids.get(1).toString(),
                grouped.path("groups").get(0).path("entries").get(0).path("entryId").asText());
        call(
                post("/api/v1/competition-units/{id}/results", u.getId()),
                owner("OPERATOR", a),
                correction,
                200);
        assertEquals(1, ruleLogs.findByEventDisciplineId(d.getId()).size());
    }

    @Test
    void listsRegistrationsWithoutEventForAdminAndManagedEventsOnlyForOrganizer() throws Exception {
        var a = athlete("F");
        var b = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var foreign = event(b.getUserId(), "REGISTRATION_OPEN");
        var own =
                submit(a, e, List.of(discipline(e, "F", BigDecimal.ZERO)))
                        .get(0)
                        .path("id")
                        .asText();
        var other =
                submit(b, foreign, List.of(discipline(foreign, "F", BigDecimal.ZERO)))
                        .get(0)
                        .path("id")
                        .asText();
        var list = call(get("/api/v1/registrations"), owner("ORGANIZER", a), null, 200);
        assertEquals(1, list.size());
        assertEquals(own, list.get(0).path("id").asText());
        var all = call(get("/api/v1/registrations"), owner("PLATFORM_ADMIN", a), null, 200);
        assertTrue(all.toString().contains(own));
        assertTrue(all.toString().contains(other));
        call(
                get("/api/v1/registrations").param("eventId", foreign.getId().toString()),
                owner("ORGANIZER", a),
                null,
                403);
    }

    @Test
    void patchChecksEventStatusAndReturnsFieldViolations() throws Exception {
        var a = athlete("F");
        var e = event(a.getUserId(), "REGISTRATION_OPEN");
        var d = discipline(e, "F", BigDecimal.ZERO);
        assertEquals(
                "competition.event_not_editable",
                call(
                                patch("/api/v1/event-disciplines/{id}", d.getId()),
                                owner("ORGANIZER", a),
                                Map.of("name", "Новое"),
                                400)
                        .path("code")
                        .asText());
        e.setStatus("PUBLISHED");
        events.saveAndFlush(e);
        var error =
                call(
                        patch("/api/v1/event-disciplines/{id}", d.getId()),
                        owner("ORGANIZER", a),
                        Map.of("participantLimit", -1, "entryFeeAmount", -10),
                        400);
        assertTrue(error.path("violations").toString().contains("participantLimit"));
        assertTrue(error.path("violations").toString().contains("entryFeeAmount"));
        error =
                call(
                        patch("/api/v1/event-disciplines/{id}", d.getId()),
                        owner("ORGANIZER", a),
                        Map.of("stroke", "WRONG"),
                        400);
        assertEquals("stroke", error.path("violations").get(0).path("field").asText());
        var u = unit(d, 1);
        error =
                call(
                        post("/api/v1/competition-units/{id}/results", u.getId()),
                        owner("OPERATOR", a),
                        List.of(Map.of("status", "INVALID", "timeMs", -1)),
                        400);
        assertTrue(error.path("violations").toString().contains("[0].competitionUnitEntryId"));
        assertTrue(error.path("violations").toString().contains("[0].status"));
    }

    @Test
    void registrationMissingDisciplineKeepsUnavailableCode() throws Exception {
        var a = athlete("F");
        assertEquals(
                "registrations.discipline_not_available",
                call(
                                post("/api/v1/registrations"),
                                a,
                                Map.of("eventDisciplineId", UUID.randomUUID()),
                                400)
                        .path("code")
                        .asText());
    }

    @Test
    void upsertPreservesOptionalProfileValuesAndFillsNormalizedKey() {
        var a = athlete("F");
        var p = profiles.findByUserId(a.getUserId()).orElseThrow();
        p.setMiddleName("Ивановна");
        p.setFirstName("Анна  Мария");
        p.setLastName(" Ёлкина ");
        profiles.saveAndFlush(p);
        var result =
                profileLookup.upsertForUser(
                        a.getUserId(),
                        new ProfileData(
                                " Ёлкина ",
                                "Анна  Мария",
                                null,
                                LocalDate.of(2015, 6, 1),
                                "FEMALE",
                                null));
        p = profiles.findById(result.id()).orElseThrow();
        assertEquals("елкина|анна мария|2015-06-01|f", p.getExternalKey());
        assertEquals("Ивановна", p.getMiddleName());
        assertEquals("Исходный клуб", p.getClubName());
    }

    @Test
    void markPaidUsesCanonicalServiceAndRejectsOtherStatuses() throws Exception {
        var a = athlete("F");
        var event = event(a.getUserId(), "REGISTRATION_OPEN");
        var discipline = discipline(event, "F", BigDecimal.ONE);
        String id = submit(a, event, List.of(discipline)).get(0).path("id").asText();
        assertEquals(
                "registrations.payment_not_applicable",
                call(
                                post("/api/v1/registrations/{id}/mark-paid", id),
                                owner("OPERATOR", a),
                                null,
                                409)
                        .path("code")
                        .asText());
        call(
                post("/api/v1/registrations/{id}/review", id),
                owner("ORGANIZER", a),
                Map.of("decision", "CONFIRMED"),
                200);
        var paid =
                call(
                        post("/api/v1/registrations/{id}/mark-paid", id),
                        owner("OPERATOR", a),
                        null,
                        200);
        assertEquals("PAID", paid.path("status").asText());
        assertEquals("PAID", paid.path("paymentStatus").asText());
        assertEquals(
                "registrations.payment_not_applicable",
                call(
                                post("/api/v1/registrations/{id}/mark-paid", id),
                                owner("OPERATOR", a),
                                null,
                                409)
                        .path("code")
                        .asText());
    }

    private JsonNode call(
            MockHttpServletRequestBuilder request,
            PlatformUserPrincipal actor,
            Object body,
            int statusCode)
            throws Exception {
        request.with(user(actor));
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String result =
                mvc.perform(request)
                        .andExpect(status().is(statusCode))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return result.isBlank() ? json.nullNode() : json.readTree(result);
    }

    private JsonNode submit(PlatformUserPrincipal a, EventEntity e, List<EventDisciplineEntity> ds)
            throws Exception {
        return call(
                post("/api/v1/public/events/{slug}/registrations", e.getPublicSlug()),
                a,
                form(a, ds),
                200);
    }

    private Map<String, Object> form(PlatformUserPrincipal a, List<EventDisciplineEntity> ds) {
        var p = profiles.findByUserId(a.getUserId()).orElseThrow();
        return Map.of(
                "profile",
                new ProfileData(
                        p.getLastName(),
                        p.getFirstName(),
                        null,
                        p.getBirthDate(),
                        p.getGender(),
                        "Новый клуб"),
                "items",
                ds.stream()
                        .map(d -> Map.of("eventDisciplineId", d.getId(), "seedTimeMs", 32150))
                        .toList());
    }

    private PlatformUserPrincipal athlete(String gender) {
        var u = new UserEntity();
        u.setEmail(UUID.randomUUID() + "@example.com");
        u.setPasswordHash("test-hash");
        u.setStatus("active");
        u = users.saveAndFlush(u);
        var p = new ProfileEntity();
        p.setUserId(u.getId());
        p.setFirstName("Анна");
        p.setLastName("Иванова" + UUID.randomUUID().toString().substring(0, 8));
        p.setBirthDate(LocalDate.of(2015, 6, 1));
        p.setGender(gender);
        p.setClubName("Исходный клуб");
        profiles.saveAndFlush(p);
        return principal(u.getId(), "PARTICIPANT");
    }

    private PlatformUserPrincipal principal(UUID id, String role) {
        return new PlatformUserPrincipal(
                id,
                id + "@example.com",
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private PlatformUserPrincipal owner(String role, PlatformUserPrincipal a) {
        return principal(a.getUserId(), role);
    }

    private EventEntity event(UUID owner, String status) {
        var now = OffsetDateTime.now();
        var e = new EventEntity();
        e.setId(UUID.randomUUID());
        e.setCreatedByUserId(owner);
        e.setOrganizationId(UUID.randomUUID());
        e.setVenueId(UUID.randomUUID());
        e.setSportId(SPORT);
        e.setRegulationVersionId(UUID.randomUUID());
        e.setTitle("Демо");
        e.setPublicSlug("demo-" + UUID.randomUUID());
        e.setStatus(status);
        e.setRegistrationOpenAt(now.minusDays(1));
        e.setRegistrationCloseAt(now.plusDays(1));
        e.setEventStartAt(now.plusDays(2));
        e.setEventEndAt(now.plusDays(3));
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return events.saveAndFlush(e);
    }

    private DisciplineTemplateEntity template() {
        var t = new DisciplineTemplateEntity();
        t.setId(UUID.randomUUID());
        t.setSportId(SPORT);
        t.setCode("swim-" + UUID.randomUUID());
        t.setName("Плавание");
        t.setCompetitionFormat("INDIVIDUAL");
        t.setUnitType("HEAT");
        t.setResultType("TIME");
        t.setRankingStrategy("ASC");
        t.setDefaultMeta("{}");
        return templates.saveAndFlush(t);
    }

    private List<AgeGroup> groups(String gender) {
        return List.of(new AgeGroup(gender + "-2014-2015", "2014–2015", gender, 2014, 2015));
    }

    private EventDisciplineEntity discipline(EventEntity e, String gender, BigDecimal fee) {
        var d = new EventDisciplineEntity();
        d.setId(UUID.randomUUID());
        d.setEventId(e.getId());
        d.setDisciplineTemplateId(template().getId());
        d.setCode(gender + "-50-FREE-" + UUID.randomUUID());
        d.setName("50 м " + gender);
        d.setCompetitionFormat("INDIVIDUAL");
        d.setUnitType("HEAT");
        d.setResultType("TIME");
        d.setRankingStrategy("ASC");
        d.setGender(gender);
        d.setDistanceM(50);
        d.setStroke("FREE");
        d.setAgeGroups(groups(gender));
        d.setStatus("PUBLISHED");
        d.setEntryFeeAmount(fee);
        d.setEntryFeeCurrency("RUB");
        d.setSettingsJson("{}");
        d.setCreatedAt(OffsetDateTime.now());
        d.setUpdatedAt(OffsetDateTime.now());
        return disciplines.saveAndFlush(d);
    }

    private RegistrationEntity registration(EventDisciplineEntity d, UUID profileId, UUID userId) {
        var r = new RegistrationEntity();
        r.setId(UUID.randomUUID());
        r.setEventId(d.getEventId());
        r.setEventDisciplineId(d.getId());
        r.setParticipantProfileId(profileId);
        r.setParticipantUserId(userId);
        r.setStatus("CONFIRMED");
        r.setSource(userId == null ? "IMPORT" : "FORM");
        r.setParticipantSnapshot(
                "{\"lastName\":\"Иванова\",\"firstName\":\"Анна\",\"gender\":\"F\",\"birthDate\":\"2015-06-01\"}");
        r.setRegistrationMeta("{}");
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        return r;
    }

    private CompetitionUnitEntity unit(EventDisciplineEntity d, int number) {
        var u = new CompetitionUnitEntity();
        u.setId(UUID.randomUUID());
        u.setEventDisciplineId(d.getId());
        u.setLabel("Заплыв " + number);
        u.setSequenceNumber(number);
        u.setStatus("SCHEDULED");
        u.setScheduledAt(OffsetDateTime.now().plusMinutes(number));
        u.setAgeGroupCode(d.getGender() + "-2014-2015");
        u.setCreatedAt(OffsetDateTime.now());
        u.setUpdatedAt(OffsetDateTime.now());
        return units.saveAndFlush(u);
    }
}
