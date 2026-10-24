package com.acme.sportplatform.security;

import java.util.List;
import java.util.Map;

import com.acme.sportplatform.identity.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecB1OrganizationRolesIntegrationTest extends Qa04SecurityFixture {
    @Autowired JwtService jwt;

    String bearer(String actor) { return "Bearer " + jwt.generateAccessToken(principal(actor), List.of()); }

    void role(String actor, String role) {
        jdbc.update("update organization_members set member_role=? where organization_id=? and user_id=?", role, id("org"), id(actor));
        em.clear();
    }

    ResultActions postRole(String actor, String target, String role) throws Exception {
        return mvc.perform(post("/api/v1/organizations/{org}/members", id("org"))
                .header("Authorization", bearer(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("userId", ids.get(target), "memberRole", role))));
    }

    ResultActions patchRole(String actor, String target, String role) throws Exception {
        return mvc.perform(patch("/api/v1/organizations/{org}/members/{user}", id("org"), id(target))
                .header("Authorization", bearer(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("memberRole", role))));
    }

    ResultActions deleteRole(String actor, String target) throws Exception {
        return mvc.perform(delete("/api/v1/organizations/{org}/members/{user}", id("org"), id(target))
                .header("Authorization", bearer(actor)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "manager", "organizer", "platform_admin"})
    void nonOwnersCannotAssignOrRemoveOwnerUsingPostPatchOrDelete(String actorRole) throws Exception {
        role("organizer", "owner");
        String actor = actorRole.equals("platform_admin") ? "platform_admin" : "operator";
        if (!actor.equals("platform_admin")) role(actor, actorRole);
        member("org", "target", "member");
        postRole(actor, "target", "OWNER").andExpect(status().isForbidden());
        patchRole(actor, "target", "owner").andExpect(status().isForbidden());
        for (var request : List.of(postRole(actor, "organizer", "member"),
                patchRole(actor, "organizer", "admin"), deleteRole(actor, "organizer"))) {
            request.andExpect(status().isForbidden()).andExpect(jsonPath("$.message").isNotEmpty());
        }
        assertThat(jdbc.queryForObject("select member_role from organization_members where organization_id=? and user_id=?",
                String.class, id("org"), id("organizer"))).isEqualTo("owner");
        assertThat(jdbc.queryForObject("select member_role from organization_members where organization_id=? and user_id=?",
                String.class, id("org"), id("target"))).isEqualTo("member");
    }

    @Test
    void adminCannotCreateNewOwnerByEmailOrUserIdButCanManageOrdinaryMembers() throws Exception {
        role("organizer", "owner"); role("operator", "admin");
        postRole("operator", "target", "owner").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Только владелец организации может назначать или снимать роль владельца"));
        String email = jdbc.queryForObject("select email from users where id=?", String.class, id("target"));
        mvc.perform(post("/api/v1/organizations/{org}/members", id("org")).header("Authorization", bearer("operator"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email", email, "memberRole", "owner"))))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from organization_members where organization_id=? and user_id=?",
                Integer.class, id("org"), id("target"))).isZero();
        postRole("operator", "target", "judge").andExpect(status().isCreated());
        patchRole("operator", "target", "operator").andExpect(status().isOk());
        deleteRole("operator", "target").andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @CsvSource({"post,organizer", "patch,organizer", "delete,organizer", "post,platform_admin", "patch,platform_admin", "delete,platform_admin"})
    void lastOwnerIsProtectedForEveryMutationIncludingPlatformAdmin(String operation, String actor) throws Exception {
        if (actor.equals("platform_admin")) member("org", actor, "owner");
        else role("organizer", "owner");
        var response = switch (operation) {
            case "post" -> postRole(actor, actor, "member");
            case "patch" -> patchRole(actor, actor, "admin");
            case "delete" -> deleteRole(actor, actor);
            default -> throw new AssertionError(operation);
        };
        response.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("organizations.last_owner"))
                .andExpect(jsonPath("$.message").value("Нельзя удалить или понизить последнего владельца организации"));
        assertThat(jdbc.queryForObject("select member_role from organization_members where organization_id=? and user_id=?",
                String.class, id("org"), id(actor))).isEqualTo("owner");
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "patch", "delete"})
    void ownerCanPromoteAnotherMemberThenLeave(String operation) throws Exception {
        role("organizer", "owner");
        postRole("organizer", "target", "owner").andExpect(status().isCreated());
        switch (operation) {
            case "post" -> postRole("organizer", "organizer", "admin").andExpect(status().isCreated());
            case "patch" -> patchRole("organizer", "organizer", "admin").andExpect(status().isOk());
            case "delete" -> deleteRole("organizer", "organizer").andExpect(status().isNoContent());
            default -> throw new AssertionError(operation);
        }
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from organization_members where organization_id=? and member_role='owner'",
                Integer.class, id("org"))).isEqualTo(1);
        patchRole("organizer", "target", "member").andExpect(status().isForbidden());
    }

    @Test
    void ownerCanPromoteWithPatchAndDemoteOrRemoveOtherOwners() throws Exception {
        role("organizer", "owner");
        patchRole("organizer", "operator", "owner").andExpect(status().isOk());
        patchRole("organizer", "operator", "admin").andExpect(status().isOk());
        postRole("organizer", "operator", "owner").andExpect(status().isCreated());
        deleteRole("organizer", "operator").andExpect(status().isNoContent());
    }

    @Test
    void approvalCannotAssignOwnerInExistingOrganizationWithoutOwnerMembership() throws Exception {
        role("organizer", "owner");
        mvc.perform(post("/api/v1/organization-registration-requests/{id}/approve", id("orgRequest"))
                .header("Authorization", bearer("platform_admin")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("organizationId", ids.get("org")))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("organizations.protected_member"));
        assertThat(jdbc.queryForObject("select status from organization_registration_requests where id=?",
                String.class, id("orgRequest"))).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from organization_members where organization_id=? and user_id=?",
                Integer.class, id("org"), id("target"))).isZero();
    }

    @Test
    void platformAdminCanBootstrapFirstOwnerForNewOrganization() throws Exception {
        mvc.perform(post("/api/v1/organization-registration-requests/{id}/approve", id("orgRequest"))
                .header("Authorization", bearer("platform_admin")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        em.flush();
        assertThat(jdbc.queryForObject("select member_role from organization_members where user_id=?",
                String.class, id("target"))).isEqualTo("owner");
    }

    @Test
    void removedCreatorLosesEventAndRegistrationAccessWithSameJwt() throws Exception {
        role("organizer", "owner"); role("operator", "owner");
        String creatorBearer = bearer("organizer");
        mvc.perform(get("/api/v1/events/{id}", id("event")).header("Authorization", creatorBearer)).andExpect(status().isOk());
        deleteRole("operator", "organizer").andExpect(status().isNoContent());
        for (String path : List.of("/api/v1/events/" + id("event"), "/api/v1/registrations?eventId=" + id("event"))) {
            mvc.perform(get(path).header("Authorization", creatorBearer)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("events.access_denied"));
        }
        mvc.perform(patch("/api/v1/events/{id}", id("event")).header("Authorization", creatorBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Запрещено\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/events/{id}/close-registration", id("event")).header("Authorization", creatorBearer))
                .andExpect(status().isForbidden());
        for (String path : List.of("/api/v1/organizer/events", "/api/v1/registrations")) {
            mvc.perform(get(path).header("Authorization", creatorBearer)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        }
        member("foreignOrg", "organizer", "owner");
        mvc.perform(get("/api/v1/events/{id}", id("event")).header("Authorization", creatorBearer)).andExpect(status().isForbidden());
        member("org", "organizer", "member");
        mvc.perform(get("/api/v1/events/{id}", id("event")).header("Authorization", creatorBearer)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/events/{id}", id("event")).header("Authorization", creatorBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Запрещено\"}")).andExpect(status().isForbidden());
    }
}
