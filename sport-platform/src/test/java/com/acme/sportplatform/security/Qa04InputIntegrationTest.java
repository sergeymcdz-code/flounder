package com.acme.sportplatform.security;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Qa04InputIntegrationTest extends Qa04SecurityFixture {
    @ParameterizedTest
    @ValueSource(strings = {"email", "phone", "firstName", "lastName", "middleName", "city", "countryCode", "clubName", "gender"})
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void selfRegistrationRejectsStringsBeyondStorageLimits(String field) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(userBody(PASSWORD));
        body.put(field, field.equals("email") ? "x".repeat(260) + "@example.org" : "x".repeat(300));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_error"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"inactive", "blocked"})
    void loginForDisabledAccountsOnlyRevealsAccountStateWithTheCorrectPassword(String state) throws Exception {
        jdbc.update("update users set status=? where id=?", state, id("participant"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", ids.get("participantEmail"), "password", PASSWORD))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("identity.user_blocked"));
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", ids.get("participantEmail"), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("identity.invalid_credentials"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("DisabledException", "blocked", "inactive", PASSWORD);
    }
    @Test
    void qa04_07_oneCharacterPasswordMustNotCreateAnAccount() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(userBody("a")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.violations[?(@.field == 'password')]").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from users where email=?", Integer.class, ids.get("newEmail"))).isZero();
    }
    @ParameterizedTest
    @ValueSource(strings = {"ascii", "unicode"})
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void selfRegistrationRejectsPasswordsBeyondBcryptByteLimit(String kind) throws Exception {
        String password = kind.equals("ascii") ? "x".repeat(73) : "ж".repeat(40);
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(userBody(password)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_error"));
    }
    @Test
    void athleteSearchRejectsOversizedQueryWithGenericValidationError() throws Exception {
        mvc.perform(get(publicUrl("/athletes")).param("q", "x".repeat(129)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("publication.invalid_query"));
    }
}
