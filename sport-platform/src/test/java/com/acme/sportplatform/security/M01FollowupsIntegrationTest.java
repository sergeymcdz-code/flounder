package com.acme.sportplatform.security;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class M01FollowupsIntegrationTest extends Qa04SecurityFixture {
    @Test
    void userListBadgesContainOnlyGlobalRolesWhileDetailsKeepBothScopes() throws Exception {
        jdbc.update("""
                insert into user_role_assignments(id,user_id,role_id,organization_id)
                select ?,?,id,? from roles where code='judge'
                """, UUID.randomUUID(), id("target"), id("org"));
        jdbc.update("""
                insert into user_role_assignments(id,user_id,role_id,event_id)
                select ?,?,id,? from roles where code='operator'
                """, UUID.randomUUID(), id("target"), id("event"));
        String email = jdbc.queryForObject("select email from users where id=?", String.class, id("target"));

        mvc.perform(get("/api/v1/users").param("query", email).with(authentication(auth("platform_admin"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ids.get("target")))
                .andExpect(jsonPath("$.items[0].roles.length()").value(1))
                .andExpect(jsonPath("$.items[0].roles[0]").value("participant"));
        mvc.perform(get(expand("/api/v1/users/$target")).with(authentication(auth("platform_admin"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(3))
                .andExpect(jsonPath("$.roles[?(@.code == 'judge')].organizationId").value(org.hamcrest.Matchers.contains(ids.get("org"))))
                .andExpect(jsonPath("$.roles[?(@.code == 'operator')].eventId").value(org.hamcrest.Matchers.contains(ids.get("event"))));
    }

    @Test
    void adminPasswordChangeUpdatesTheUserTimestamp() throws Exception {
        OffsetDateTime previous = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        jdbc.update("update users set updated_at=? where id=?", previous, id("target"));

        mvc.perform(patch(expand("/api/v1/users/$target/password"))
                .with(authentication(auth("platform_admin"))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"M01-new-password!\"}"))
                .andExpect(status().isNoContent());
        em.flush();

        assertThat(jdbc.queryForObject("select updated_at from users where id=?", OffsetDateTime.class, id("target")))
                .isAfter(previous);
    }
}
