package com.acme.sportplatform.identity;

import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AdminPanelIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    String admin;
    String adminId;

    @BeforeEach
    void loginAdmin() throws Exception {
        admin = login("admin@flounder.demo", "Demo2026!");
        adminId = jdbc.queryForObject("select id::text from users where email = 'admin@flounder.demo'", String.class);
    }
    ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    String login(String email, String password) throws Exception {
        return body(call(post("/api/v1/auth/login"), null, Map.of("email", email, "password", password)).andExpect(status().isOk())).get("accessToken").asText();
    }
    String create(String email, String firstName, String lastName) throws Exception {
        return body(call(post("/api/v1/users"), admin, Map.of("email", email, "password", "Demo2026!", "firstName", firstName,
                "lastName", lastName, "birthDate", "1990-01-01", "roles", java.util.List.of("participant")))
                .andExpect(status().isCreated())).get("id").asText();
    }
    String email() { return UUID.randomUUID() + "@test.example"; }

    @Test
    void searchPaginationRoleAndLiteralWildcards() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String first = create("m01-" + suffix + "-one@test.example", "Поиск" + suffix, "Иванов");
        String second = create("m01-" + suffix + "-two@test.example", "Поиск" + suffix, "Петров");
        call(post("/api/v1/users/" + first + "/roles"), admin, Map.of("roleCode", "judge")).andExpect(status().isCreated());
        var page0 = body(call(get("/api/v1/users").param("query", "ПОИСК" + suffix).param("size", "1"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.size").value(1)));
        var page1 = body(call(get("/api/v1/users").param("query", suffix).param("size", "1").param("page", "1"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)));
        assertThat(page0.get("items").get(0).get("id").asText()).isEqualTo(second);
        assertThat(page1.get("items").get(0).get("id").asText()).isEqualTo(first);
        call(get("/api/v1/users").param("query", suffix).param("role", "judge"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(first));
        call(get("/api/v1/users").param("query", "ИВАНОВ"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + first + "')]").isNotEmpty());
        call(get("/api/v1/users").param("size", "101"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/v1/users").param("page", "-1"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/v1/users").param("query", "%"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void roleGrantRevocationAndScopedDetails() throws Exception {
        String id = create(email(), "Роли", "Проверка");
        call(post("/api/v1/users/" + id + "/roles"), admin, Map.of("roleCode", "judge")).andExpect(status().isCreated());
        call(post("/api/v1/users/" + id + "/roles"), admin, Map.of("roleCode", "judge")).andExpect(status().isOk());
        call(get("/api/v1/users/" + id), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.roles[?(@.code == 'judge')].name").isNotEmpty());
        call(delete("/api/v1/users/" + id + "/roles/judge"), admin, null).andExpect(status().isNoContent());
        call(get("/api/v1/users/" + id), admin, null).andExpect(jsonPath("$.roles[?(@.code == 'judge')]").isEmpty());
        call(get("/api/v1/roles"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code == 'platform_admin')]").isNotEmpty());
        String org = body(call(post("/api/v1/organizations"), admin, Map.of("type", "club", "name", "Роли с областью"))
                .andExpect(status().isCreated())).get("id").asText();
        jdbc.update("insert into user_role_assignments(id,user_id,role_id,organization_id) select ?::uuid, ?::uuid, id, ?::uuid from roles where code = 'judge'",
                UUID.randomUUID().toString(), id, org);
        call(get("/api/v1/users/" + id), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.code == 'judge')].organizationId").value(org));

    }

    @Test
    void oldTokenReflectsRoleChangesAndZeroRolesRemainAuthenticated() throws Exception {
        String email = email(); String id = create(email, "Живые", "Роли");
        String token = login(email, "Demo2026!");
        call(get("/api/v1/users"), token, null).andExpect(status().isForbidden());
        call(post("/api/v1/users/" + id + "/roles"), admin, Map.of("roleCode", "platform_admin")).andExpect(status().isCreated());
        call(get("/api/v1/users"), token, null).andExpect(status().isOk());
        call(delete("/api/v1/users/" + id + "/roles/platform_admin"), admin, null).andExpect(status().isNoContent());
        call(delete("/api/v1/users/" + id + "/roles/participant"), admin, null).andExpect(status().isNoContent());
        call(get("/api/v1/users"), token, null).andExpect(status().isForbidden());
        call(get("/api/v1/auth/me"), token, null).andExpect(status().isOk());
        token = login(email, "Demo2026!");
        call(get("/api/v1/auth/me"), token, null).andExpect(status().isOk());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"member", "organizer", "operator", "judge"})
    void ordinaryOrganizationMembersCannotManageMembership(String role) throws Exception {
        String email = email(); String id = create(email, "Обычный", "Член"); String token = login(email, "Demo2026!");
        String org = body(call(post("/api/v1/organizations"), admin, Map.of("type", "club", "name", "Проверка доступа"))
                .andExpect(status().isCreated())).get("id").asText();
        call(post("/api/v1/organizations/" + org + "/members"), admin, Map.of("userId", id, "memberRole", role)).andExpect(status().isCreated());
        call(get("/api/v1/organizations/" + org + "/members"), token, null).andExpect(status().isForbidden());
        call(post("/api/v1/organizations/" + org + "/members"), token, Map.of("userId", adminId, "memberRole", "admin")).andExpect(status().isForbidden());
        call(patch("/api/v1/organizations/" + org + "/members/" + id), token, Map.of("memberRole", "owner")).andExpect(status().isForbidden());
        call(delete("/api/v1/organizations/" + org + "/members/" + id), token, null).andExpect(status().isForbidden());
    }

    @Test
    void blockRejectsLoginAndOldJwtThenUnblockRestoresAccess() throws Exception {
        String email = email(); String id = create(email, "Блокировка", "Проверка");
        String token = login(email, "Demo2026!");
        call(get("/api/v1/auth/me"), token, null).andExpect(status().isOk());
        call(patch("/api/v1/users/" + id), admin, Map.of("status", "BLOCKED")).andExpect(status().isOk());
        call(post("/api/v1/auth/login"), null, Map.of("email", email, "password", "Demo2026!"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("identity.user_blocked"));
        call(get("/api/v1/auth/me"), token, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("identity.user_blocked"));
        call(patch("/api/v1/users/" + id), admin, Map.of("status", "ACTIVE")).andExpect(status().isOk());
        call(get("/api/v1/auth/me"), token, null).andExpect(status().isOk());
        login(email, "Demo2026!");
    }

    @Test
    void selfProtectionAndPasswordChange() throws Exception {
        call(patch("/api/v1/users/" + adminId), admin, Map.of("status", "BLOCKED")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("identity.cannot_modify_self"));
        call(delete("/api/v1/users/" + adminId + "/roles/platform_admin"), admin, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("identity.cannot_modify_self"));
        String email = email(); String id = create(email, "Пароль", "Проверка");
        call(patch("/api/v1/users/" + id + "/password"), admin, Map.of("newPassword", "short")).andExpect(status().isBadRequest());
        call(patch("/api/v1/users/" + id + "/password"), admin, Map.of("newPassword", "NewPass2026!")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select password_hash from users where id = ?::uuid", String.class, id)).startsWith("$2");
        call(post("/api/v1/auth/login"), null, Map.of("email", email, "password", "Demo2026!")).andExpect(status().isUnauthorized());
        login(email, "NewPass2026!");
    }

    @Test
    void membershipByEmailRoleChangeDeleteAndDetails() throws Exception {
        String email = email(); String user = create(email, "Член", "Организации");
        String org = body(call(post("/api/v1/organizations"), admin, Map.of("type", "club", "name", "Клуб M01", "city", "Самара"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.city").value("Самара"))).get("id").asText();
        call(post("/api/v1/organizations/" + org + "/members"), admin, Map.of("email", email.toUpperCase(), "memberRole", "owner"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.firstName").value("Член"));
        call(get("/api/v1/users/" + user), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.memberships[0].organizationId").value(org));
        call(get("/api/v1/organizations/" + org + "/members"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$[0].lastName").value("Организации"));
        call(patch("/api/v1/organizations/" + org + "/members/" + user), admin, Map.of("memberRole", "admin")).andExpect(status().isOk()).andExpect(jsonPath("$.memberRole").value("admin"));
        String memberToken = login(email, "Demo2026!");
        // A local organization admin does not need a global organizer role.
        call(get("/api/v1/organizations/" + org + "/members"), memberToken, null).andExpect(status().isOk());
        String other = create(email(), "Другой", "Участник");
        call(post("/api/v1/organizations/" + org + "/members"), memberToken, Map.of("userId", other, "memberRole", "judge")).andExpect(status().isCreated());
        call(patch("/api/v1/organizations/" + org + "/members/" + other), memberToken, Map.of("memberRole", "operator")).andExpect(status().isOk());
        call(delete("/api/v1/organizations/" + org + "/members/" + other), memberToken, null).andExpect(status().isNoContent());
        call(post("/api/v1/organizations/" + org + "/members"), admin, Map.of("email", "missing@test.example", "memberRole", "judge"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("organizations.user_not_found"));
        call(delete("/api/v1/organizations/" + org + "/members/" + user), admin, null).andExpect(status().isNoContent());
        call(get("/api/v1/users/" + user), admin, null).andExpect(jsonPath("$.memberships").isEmpty());
    }

    @Test
    void nonAdminCannotUseAdminEndpointsOrEscalateDuringSignup() throws Exception {
        String email = email(); String id = create(email, "Доступ", "Проверка");
        String token = login(email, "Demo2026!");
        for (String path : java.util.List.of("/api/v1/users", "/api/v1/users/" + id, "/api/v1/roles", "/api/v1/organization-registration-requests?status=PENDING"))
            call(get(path), token, null).andExpect(status().isForbidden());
        call(patch("/api/v1/users/" + id), token, Map.of("status", "BLOCKED")).andExpect(status().isForbidden());
        call(patch("/api/v1/users/" + id + "/password"), token, Map.of("newPassword", "Changed2026!")).andExpect(status().isForbidden());
        call(post("/api/v1/users/" + id + "/roles"), token, Map.of("roleCode", "platform_admin")).andExpect(status().isForbidden());
        call(delete("/api/v1/users/" + id + "/roles/participant"), token, null).andExpect(status().isForbidden());
        String forbiddenEmail = email();
        call(post("/api/v1/users"), null, Map.of("email", forbiddenEmail, "password", "Demo2026!", "firstName", "Подмена", "lastName", "Роли",
                "birthDate", "1990-01-01", "roles", java.util.List.of("platform_admin")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("identity.access_denied"));
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Long.class, forbiddenEmail)).isZero();
    }

    @Test
    void registrationQueueApproveAndReject() throws Exception {
        String email = email(); create(email, "Заявитель", "Организации"); String token = login(email, "Demo2026!");
        String request = body(call(post("/api/v1/organization-registration-requests"), token,
                Map.of("organizationType", "club", "organizationName", "Клуб по заявке", "contactEmail", email)).andExpect(status().isCreated())).get("id").asText();
        call(get("/api/v1/organization-registration-requests").param("status", "PENDING"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + request + "')]").isNotEmpty());
        call(post("/api/v1/organization-registration-requests/" + request + "/approve"), token, Map.of()).andExpect(status().isForbidden());
        call(post("/api/v1/organization-registration-requests/" + request + "/approve"), admin, Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        call(post("/api/v1/organization-registration-requests/" + request + "/approve"), admin, Map.of()).andExpect(status().isConflict());
        request = body(call(post("/api/v1/organization-registration-requests"), token,
                Map.of("organizationType", "club", "organizationName", "Другой клуб")).andExpect(status().isCreated())).get("id").asText();
        call(post("/api/v1/organization-registration-requests/" + request + "/reject"), admin, Map.of("comment", "Уточните реквизиты"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").value("Уточните реквизиты"));
    }
}
