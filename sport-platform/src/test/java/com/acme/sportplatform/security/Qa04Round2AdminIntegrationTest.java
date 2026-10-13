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

class Qa04Round2AdminIntegrationTest extends Qa04SecurityFixture {
    @ParameterizedTest
    @ValueSource(strings = {"participant", "judge", "operator", "organizer", "foreign_organizer"})
    void nonAdminsCannotPatchOwnOrForeignStatusRolesOrPassword(String actor) throws Exception {
        for (String target : List.of(actor, "target")) {
            mvc.perform(patch("/api/v1/users/{id}", id(target)).with(authentication(auth(actor)))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\",\"roles\":[\"platform_admin\"]}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/v1/users/{id}/password", id(target)).with(authentication(auth(actor)))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Attacker123!\"}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, id("target"))).isEqualTo("active");
        assertThat(jdbc.queryForObject("select password_hash from users where id=?", String.class, id("target"))).isEqualTo(HASH);
    }
    @Test
    void adminCannotDisableDeleteOrStripOwnRoleAndPatchCannotAssignRoles() throws Exception {
        jdbc.update("insert into profiles(id,user_id,first_name,last_name,birth_date,gender) values (gen_random_uuid(),?,'Админ','QA04','1990-01-01','M')", id("platform_admin"));
        mvc.perform(patch("/api/v1/users/{id}", id("platform_admin")).with(authentication(auth("platform_admin")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"bLoCkEd\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("identity.cannot_modify_self"));
        mvc.perform(delete("/api/v1/users/{id}", id("platform_admin")).with(authentication(auth("platform_admin"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("identity.cannot_modify_self"));
        mvc.perform(delete("/api/v1/users/{id}/roles/platform_admin", id("platform_admin")).with(authentication(auth("platform_admin"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("identity.cannot_modify_self"));
        mvc.perform(patch("/api/v1/users/{id}", id("target")).with(authentication(auth("platform_admin")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\",\"roles\":[\"platform_admin\"]}"))
                .andExpect(status().isOk());
        em.flush();
        assertThat(jdbc.queryForList("select r.code from user_role_assignments a join roles r on r.id=a.role_id where user_id=?", String.class, id("target"))).containsExactly("participant");
        assertThat(jdbc.queryForObject("select status from users where id=?", String.class, id("platform_admin"))).isEqualTo("active");
    }
    @Test
    void organizationOwnerCannotModifyForeignMembershipOrGlobalUser() throws Exception {
        jdbc.update("update organization_members set member_role='owner' where organization_id=? and user_id=?", id("org"), id("organizer"));
        member("org", "target", "member"); member("foreignOrg", "target", "judge");
        for (var actor : List.of("organizer", "foreign_organizer")) {
            String foreignOrg = actor.equals("organizer") ? "foreignOrg" : "org";
            mvc.perform(patch("/api/v1/organizations/{org}/members/{user}", id(foreignOrg), id("target"))
                    .with(authentication(auth(actor))).contentType(MediaType.APPLICATION_JSON).content("{\"memberRole\":\"owner\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/organizations/{org}/members/{user}", id(foreignOrg), id("target"))
                    .with(authentication(auth(actor)))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/users/{id}", id("target")).with(authentication(auth(actor)))).andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/v1/organizations/{org}/members/{user}", id("org"), id("target"))
                .with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON).content("{\"memberRole\":\"operator\"}"))
                .andExpect(status().isOk());
        em.flush();
        assertThat(jdbc.queryForObject("select member_role from organization_members where organization_id=? and user_id=?", String.class, id("foreignOrg"), id("target"))).isEqualTo("judge");
        assertThat(jdbc.queryForList("select r.code from user_role_assignments a join roles r on r.id=a.role_id where user_id=?", String.class, id("target"))).containsExactly("participant");
    }
    @Test
    void qa03ConflictProtectsTheExistingAccountProfile() throws Exception {
        String key = com.acme.sportplatform.identity.ProfileExternalKey.build("Петров", "Чужой", java.time.LocalDate.of(2014,3,5), "M");
        jdbc.update("update profiles set user_id=null,first_name='Чужой',last_name='Петров',external_key=? where id=?", key, id("foreignProfile"));
        mvc.perform(post(publicUrl("/registrations")).with(authentication(auth("participant")))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                        "profile", Map.of("lastName","Петров","firstName","Чужой","birthDate","2014-03-05","gender","M"),
                        "items", List.of(Map.of("eventDisciplineId",ids.get("newDiscipline"),"seedTimeMs",32150))))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("identity.profile_already_attached"));
        assertThat(jdbc.queryForObject("select user_id from profiles where id=?", java.util.UUID.class, id("foreignProfile"))).isNull();
    }
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users", "/api/v1/auth/register"})
    void qa04_10_unverifiedSignupCannotClaimImportedProfile(String endpoint) throws Exception {
        String key = com.acme.sportplatform.identity.ProfileExternalKey.build("Иванов", "Иван", java.time.LocalDate.of(2014,3,5), "M");
        jdbc.update("update profiles set user_id=null,external_key=? where id=?", key, id("foreignProfile"));
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(userBody(PASSWORD));
        body.put("lastName","Иванов").put("firstName","Иван");
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("identity.profile_already_attached"));
        assertThat(jdbc.queryForObject("select user_id from profiles where id=?", java.util.UUID.class, id("foreignProfile"))).isNull();
        assertThat(jdbc.queryForObject("select count(*) from users where email=?", Integer.class, ids.get("newEmail"))).isZero();
        assertThat(jdbc.queryForObject("select status from registrations where id=?", String.class, id("foreignRegistration"))).isEqualTo("CONFIRMED");
    }
}
