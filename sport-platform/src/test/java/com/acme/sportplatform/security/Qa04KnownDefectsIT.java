package com.acme.sportplatform.security;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Unfixed security requirements. Explicitly run with -Dtest=Qa04KnownDefectsIT (expected RED).
 * Surefire excludes *IT; Failsafe excludes the known-defect tag from ordinary verify.
 * No disabled assertions: these tests must turn green when the report's defects are repaired. */
@Tag("known-defect")
class Qa04KnownDefectsIT extends Qa04SecurityFixture {
    /** Owner: M09 — disable legacy V020 demo credentials; future migrations V035+ only.
     * DEMO_SEED is not set here: the application does not read this variable. */
    @Test
    void qa04_06_seedDisabledMustNotAllowPublicDemoAdminCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "admin@flounder.demo", "password", "Demo2026!"))))
                .andExpect(status().isUnauthorized());
    }
    /** Resolved by round 2: reject signup claims until the ownership-confirmation task exists.
     * The same scenario also runs in Qa04Round2AdminIntegrationTest in the default suite. */
    @Test
    void qa04_10_importedAthleteCannotBeClaimedByUnverifiedSelfRegistration() throws Exception {
        String key = com.acme.sportplatform.identity.ProfileExternalKey.build(
                "Иванов", "Иван", java.time.LocalDate.of(2014, 3, 5), "M");
        jdbc.update("update profiles set user_id=null, external_key=? where id=?", key, id("foreignProfile"));
        em.clear();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(userBody(PASSWORD));
        body.put("lastName", "Иванов").put("firstName", "Иван");
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isConflict());
        em.flush(); em.clear();
        assertThat(jdbc.queryForObject("select user_id from profiles where id=?", java.util.UUID.class, id("foreignProfile"))).isNull();
        assertThat(jdbc.queryForObject("select count(*) from users where email=?", Integer.class, ids.get("newEmail"))).isZero();
        mvc.perform(post(expand("/api/v1/registrations/$foreignRegistration/withdraw"))
                .with(authentication(auth("participant"))))
                .andExpect(status().isForbidden());
    }
}
