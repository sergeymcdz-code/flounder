package com.acme.sportplatform.security;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Qa04DataAccessIntegrationTest extends Qa04SecurityFixture {
    @Test
    void foreignIdsCannotBeReadOrMutatedAndUnfilteredRegistrationsAreScoped() throws Exception {
        for (String path : List.of("/api/v1/events/$foreignEvent",
                "/api/v1/events/$foreignEvent/disciplines", "/api/v1/events/$foreignEvent/sessions",
                "/api/v1/event-disciplines/$foreignDiscipline/competition-units",
                "/api/v1/event-disciplines/$foreignDiscipline/results",
                "/api/v1/competition-units/$foreignUnit", "/api/v1/registrations/$foreignRegistration"))
            mvc.perform(get(expand(path)).with(authentication(auth("organizer"))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("events.access_denied"));
        for (String suffix : List.of("/review", "/mark-paid", "/withdraw"))
            mvc.perform(post(expand("/api/v1/registrations/$foreignRegistration" + suffix))
                    .with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"CONFIRMED\"}"))
                    .andExpect(status().isForbidden());
        mvc.perform(patch(expand("/api/v1/registrations/$foreignRegistration"))
                .with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"seedTimeMs\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(expand("/api/v1/registrations/$foreignRegistration/withdraw"))
                .with(authentication(auth("participant"))))
                .andExpect(status().isForbidden());
        var response = mvc.perform(get("/api/v1/registrations").with(authentication(auth("organizer"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains(ids.get("registration")).doesNotContain(ids.get("foreignRegistration"));
        assertThat(jdbc.queryForObject("select status from registrations where id=?", String.class, id("foreignRegistration"))).isEqualTo("CONFIRMED");
    }
    @Test
    void foreignChildrenCannotBeSmuggledIntoAnOwnedEventOrUnit() throws Exception {
        mvc.perform(post(expand("/api/v1/events/$event/disciplines/$foreignDiscipline/publish"))
                .with(authentication(auth("organizer"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("competition.discipline_event_mismatch"));
        mvc.perform(put(expand("/api/v1/events/$event/live")).with(authentication(auth("organizer")))
                .contentType(MediaType.APPLICATION_JSON).content(expand("{\"currentUnitId\":\"$foreignUnit\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("results.unit_event_mismatch"));
        mvc.perform(post(expand("/api/v1/competition-units/$unit/results")).with(authentication(auth("judge")))
                .contentType(MediaType.APPLICATION_JSON).content(expand("{\"competitionUnitEntryId\":\"$foreignEntry\",\"rawValue\":\"00:01.00\",\"status\":\"PENDING\"}")))
                .andExpect(status().isForbidden());
        mvc.perform(post(expand("/api/v1/competition-units/$unit/entries")).with(authentication(auth("organizer")))
                .contentType(MediaType.APPLICATION_JSON).content(expand("{\"registrationId\":\"$foreignRegistration\",\"laneOrPosition\":2}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("results.registration_discipline_mismatch"));
        mvc.perform(post(publicUrl("/registrations")).with(authentication(auth("participant")))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                        "profile", Map.of("lastName", "Иванов", "firstName", "Иван", "birthDate", "2014-03-05", "gender", "M"),
                        "items", List.of(Map.of("eventDisciplineId", ids.get("foreignDiscipline"), "seedTimeMs", 1))))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select time_ms from results where competition_unit_entry_id=?", Long.class, id("foreignEntry"))).isEqualTo(32150);
        assertThat(jdbc.queryForObject("select status from competition_units where id=?", String.class, id("foreignUnit"))).isEqualTo("FINISHED");
    }
    @Test
    void selfRegistrationIgnoresInjectedRolesMembershipAndStatus() throws Exception {
        var body = json.readTree(userBody(PASSWORD));
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) body;
        object.put("status", "platform_admin").put("memberRole", "owner").put("organizationId", ids.get("org"));
        object.putArray("roles").add("platform_admin");
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isCreated());
        em.flush();
        var user = jdbc.queryForObject("select id from users where email=?", java.util.UUID.class, ids.get("newEmail"));
        assertThat(jdbc.queryForList("select r.code from roles r join user_role_assignments a on a.role_id=r.id where a.user_id=?", String.class, user)).containsExactly("participant");
        assertThat(jdbc.queryForObject("select count(*) from organization_members where user_id=?", Integer.class, user)).isZero();
    }
    @Test
    void onlyOrganizationOwnerOrAdminCanAssignMemberRolesAndNoGlobalRoleIsGranted() throws Exception {
        String body = expand("{\"userId\":\"$target\",\"memberRole\":\"owner\"}");
        for (String actor : List.of("participant", "judge", "operator", "organizer", "foreign_organizer"))
            mvc.perform(post(expand("/api/v1/organizations/$org/members")).with(authentication(auth(actor)))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        jdbc.update("update organization_members set member_role='owner' where organization_id=? and user_id=?", id("org"), id("organizer"));
        em.clear();
        mvc.perform(post(expand("/api/v1/organizations/$org/members")).with(authentication(auth("organizer")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        assertThat(jdbc.queryForList("select r.code from roles r join user_role_assignments a on a.role_id=r.id where a.user_id=?", String.class, id("target"))).containsExactly("participant");
        mvc.perform(post(expand("/api/v1/organizations/$org/members")).with(authentication(auth("organizer")))
                .contentType(MediaType.APPLICATION_JSON).content(expand("{\"userId\":\"$target\",\"memberRole\":\"platform_admin\"}")))
                .andExpect(status().isBadRequest());
    }
    @Test
    void publicReadsDoNotExposePrivateFieldsOrSnapshotValues() throws Exception {
        for (String path : List.of(publicUrl(""), publicUrl("/start-list"), publicUrl("/schedule"),
                publicUrl("/live"), publicUrl("/results"), publicUrl("/athletes"), publicUrl("/registration-form"),
                "/api/v1/public/events", expand("/api/v1/public/competition-units/$unit/results"),
                expand("/api/v1/public/events/$event/disciplines/$discipline/results"))) {
            var response = mvc.perform(get(path).param("q", "Ива")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("\"email\"", "\"phone\"", "\"birthDate\"", "\"address\"",
                    "private-email-qa04", "private-phone-qa04", "private-address-qa04", "2014-03-05", "password", "participantSnapshot");
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"' OR 1=1 --", "'; DROP TABLE profiles; --", "%", "_", "\\", "' UNION SELECT NULL --"})
    void nativeSlugAndAthleteQueriesTreatInjectionAsLiteralData(String payload) throws Exception {
        mvc.perform(get("/api/v1/public/events/{slug}", payload)).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isIn(400, 404));
        mvc.perform(get(publicUrl("/athletes")).param("q", payload)).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        assertThat(jdbc.queryForObject("select count(*) from profiles where id in (?,?)", Integer.class, id("profile"), id("foreignProfile"))).isEqualTo(2);
    }
    @Test
    void corsAndActuatorEnforceOriginsAndGlobalRole() throws Exception {
        mvc.perform(options(publicUrl("/live")).header("Origin", "https://qa04.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://qa04.example"));
        for (String origin : List.of("https://evil.example", "http://localhost:5173", "null"))
            mvc.perform(options(publicUrl("/live")).header("Origin", origin).header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        for (String actor : List.of("participant", "judge", "operator", "organizer", "foreign_organizer"))
            mvc.perform(get("/actuator/env").with(authentication(auth(actor)))).andExpect(status().isForbidden());
        // Disabled endpoint: an admin clears the security gate, then receives 404.
        mvc.perform(get("/actuator/env").with(authentication(auth("platform_admin")))).andExpect(status().isNotFound());
        mvc.perform(get(publicUrl(""))).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
