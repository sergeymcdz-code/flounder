package com.acme.sportplatform.security;

import java.util.ArrayList;
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
    /** Owner: M09 — bootstrap admin from environment; migrations V028+ only.
     * DEMO_SEED is not set here: the application does not read this variable. */
    @Test
    void qa04_06_seedDisabledMustNotAllowPublicDemoAdminCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "admin@flounder.demo", "password", "Demo2026!"))))
                .andExpect(status().isUnauthorized());
    }
    /** Owner: separate task "Подтверждение владения импортированным профилем". */
    @Test
    void qa04_10_importedAthleteCannotBeClaimedByUnverifiedSelfRegistration() throws Exception {
        String key = com.acme.sportplatform.identity.ProfileExternalKey.build(
                "Иванов", "Иван", java.time.LocalDate.of(2014, 3, 5), "M");
        jdbc.update("update profiles set user_id=null, external_key=? where id=?", key, id("foreignProfile"));
        em.clear();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(userBody(PASSWORD));
        body.put("lastName", "Иванов").put("firstName", "Иван");
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isCreated());
        em.flush(); em.clear();
        var attacker = jdbc.queryForObject("select id from users where email=?", java.util.UUID.class, ids.get("newEmail"));
        var p = new com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal(attacker,
                ids.get("newEmail"), "", "active", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PARTICIPANT")));
        mvc.perform(post(expand("/api/v1/registrations/$foreignRegistration/withdraw"))
                .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()))))
                .andExpect(status().isForbidden());
    }
    /** Owner: M02 — login rate limiting. */
    @Test
    void qa04_08_repeatedFailedLoginMustEventuallyBeRateLimited() throws Exception {
        var codes = new ArrayList<Integer>();
        for (int attempt = 0; attempt < 50; attempt++)
            codes.add(mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr("192.0.2.44"); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("email", "unknown@qa04.example", "password", "incorrect"))))
                    .andReturn().getResponse().getStatus());
        assertThat(codes).as("QA04-08: 50 consecutive failed logins from one IP").contains(429);
    }
}
