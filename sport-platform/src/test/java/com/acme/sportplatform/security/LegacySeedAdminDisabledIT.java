package com.acme.sportplatform.security;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression coverage for the retired migration account and resolved signup ownership checks. */
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class LegacySeedAdminDisabledIT extends Qa04SecurityFixture {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

    private static final java.util.UUID LEGACY_ID = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Test
    void qa04_06_seedDisabledMustNotAllowPublicDemoAdminCredentials() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "admin@flounder.demo", "password", "Demo2026!"))))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void migratedAccountRejectsPasswordsWithoutExceptionsOrRefreshTokens(
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        int logStart = output.getAll().length();
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, LEGACY_ID)).isEqualTo("blocked");
        String hash = jdbc.queryForObject("select password_hash from users where id=?", String.class, LEGACY_ID);
        assertThat(hash).isEqualTo("!disabled");
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        for (String password : java.util.List.of("Demo2026!", "Another-test-password1!", "!disabled")) {
            assertThat(encoder.matches(password, hash)).isFalse();
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("email", "admin@flounder.demo", "password", password))))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id=?", Integer.class, LEGACY_ID)).isZero();
        assertThat(output.getAll().substring(logStart)).doesNotContain("Exception", "ERROR");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void upgradeRevokesExistingRefreshTokensAndPreservesOtherAccounts() throws Exception {
        String schema = "legacy_upgrade_" + java.util.UUID.randomUUID().toString().replace("-", "");
        // Keep schema changes away from the application's pooled connections.
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                environment.getRequiredProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.datasource.username"),
                environment.getRequiredProperty("spring.datasource.password"));
        org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("38").load().migrate();
        try (var connection = dataSource.getConnection()) {
            connection.setSchema(schema);
            var isolated = new org.springframework.jdbc.core.JdbcTemplate(
                    new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            var other = com.acme.sportplatform.support.TestAdmin.create(isolated);
            isolated.update("insert into refresh_tokens(id,user_id,token_hash,expires_at) values (?,?,?,now()+interval '1 day')",
                    java.util.UUID.randomUUID(), LEGACY_ID, "legacy-test-token");
            isolated.update("insert into refresh_tokens(id,user_id,token_hash,expires_at) values (?,?,?,now()+interval '1 day')",
                    java.util.UUID.randomUUID(), other.id(), "other-test-token");
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(isolated.queryForObject("select status from users where id=?", String.class, LEGACY_ID)).isEqualTo("blocked");
            assertThat(isolated.queryForObject("select password_hash from users where id=?", String.class, LEGACY_ID)).isEqualTo("!disabled");
            assertThat(isolated.queryForObject("select count(*) from refresh_tokens where user_id=?", Integer.class, LEGACY_ID)).isZero();
            assertThat(isolated.queryForObject("select status from users where id=?", String.class, other.id())).isEqualTo("active");
            assertThat(isolated.queryForObject("select count(*) from refresh_tokens where user_id=?", Integer.class, other.id())).isEqualTo(1);
        } finally {
            jdbc.execute("drop schema " + schema + " cascade");
        }
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
