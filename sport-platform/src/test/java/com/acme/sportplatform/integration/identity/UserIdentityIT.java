package com.acme.sportplatform.integration.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserIdentityIntegrationTest extends AbstractPostgresIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Test
        void shouldReturnForbiddenWhenParticipantFetchesUserById() throws Exception {
                String email = uniqueEmail("participant");
                String userId = createUser(email, "secret123", "Anna", "Smirnova");
                String accessToken = login(email, "secret123");

                mockMvc.perform(get("/api/v1/users/{id}", userId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                        .andExpect(status().isForbidden());
        }

        @Test
        void shouldFetchUserByIdWhenAdmin() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String adminEmail = uniqueEmail("admin");
                String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
                assignRole(adminUserId, "platform_admin");
                String adminToken = login(adminEmail, "secret123");

                mockMvc.perform(get("/api/v1/users/{id}", targetUserId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.email").value(targetEmail))
                        .andExpect(jsonPath("$.profile.firstName").value("Anna"))
                        .andExpect(jsonPath("$.profile.lastName").value("Smirnova"));
        }

        @Test
        void shouldPatchUserWhenAdmin() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String adminEmail = uniqueEmail("admin");
                String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
                assignRole(adminUserId, "platform_admin");
                String adminToken = login(adminEmail, "secret123");

                String patchPayload = """
                        {
                          "phone": "79990001122",
                          "city": "Saint Petersburg",
                          "clubName": "Neva Club"
                        }
                        """;

                mockMvc.perform(patch("/api/v1/users/{id}", targetUserId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(patchPayload))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.phone").value("79990001122"))
                        .andExpect(jsonPath("$.profile.city").value("Saint Petersburg"))
                        .andExpect(jsonPath("$.profile.clubName").value("Neva Club"));
        }

        @Test
        void shouldReturnForbiddenWhenParticipantPatchesUser() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String participantEmail = uniqueEmail("participant");
                createUser(participantEmail, "secret123", "Petr", "Ivanov");
                String participantToken = login(participantEmail, "secret123");

                String patchPayload = """
                        {
                          "city": "Kazan"
                        }
                        """;

                mockMvc.perform(patch("/api/v1/users/{id}", targetUserId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(patchPayload))
                        .andExpect(status().isForbidden());
        }

        @Test
        void shouldSoftDeleteUserWhenAdmin() throws Exception {
            String targetEmail = uniqueEmail("target");
            String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

            String adminEmail = uniqueEmail("admin");
            String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
            assignRole(adminUserId, "platform_admin");
            String adminToken = login(adminEmail, "secret123");

            mockMvc.perform(delete("/api/v1/users/{id}", targetUserId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            String statusValue = jdbcTemplate.queryForObject(
                    "select status from users where id = ?::uuid",
                    String.class,
                    targetUserId
            );

            org.junit.jupiter.api.Assertions.assertEquals("inactive", statusValue);
        }

        @Test
        void shouldReturnValidationErrorForInvalidUuid() throws Exception {
                String adminEmail = uniqueEmail("admin");
                String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
                assignRole(adminUserId, "platform_admin");
                String adminToken = login(adminEmail, "secret123");

                mockMvc.perform(get("/api/v1/users/{id}", "1")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("validation_error"))
                        .andExpect(jsonPath("$.violations[0].field").value("userId"));
        }

        @Test
        void shouldReturnNotFoundForMissingUuid() throws Exception {
                String adminEmail = uniqueEmail("admin");
                String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
                assignRole(adminUserId, "platform_admin");
                String adminToken = login(adminEmail, "secret123");

                mockMvc.perform(get("/api/v1/users/{id}", "21111111-1111-1111-1111-111111111111")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("identity.user_not_found"));
        }

        @Test
        void shouldAssignRoleWhenAdmin() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String adminEmail = uniqueEmail("admin");
                String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
                assignRole(adminUserId, "platform_admin");
                String adminToken = login(adminEmail, "secret123");

                String payload = """
                        {
                          "roleCode": "organizer"
                        }
                        """;

                mockMvc.perform(post("/api/v1/users/{id}/roles", targetUserId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isCreated());

                Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from user_role_assignments ura
                        join roles r on r.id = ura.role_id
                        where ura.user_id = ?::uuid
                          and r.code = ?
                          and ura.event_id is null
                          and ura.organization_id is null
                        """, Integer.class, targetUserId, "organizer");

                org.junit.jupiter.api.Assertions.assertEquals(1, count);
        }

        @Test
        void shouldReturnOkWhenRoleAlreadyAssigned() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String adminEmail = uniqueEmail("admin");
                String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
                assignRole(adminUserId, "platform_admin");
                String adminToken = login(adminEmail, "secret123");

                assignRole(targetUserId, "organizer");

                String payload = """
                        {
                          "roleCode": "organizer"
                        }
                        """;

                mockMvc.perform(post("/api/v1/users/{id}/roles", targetUserId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isOk());

                Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from user_role_assignments ura
                        join roles r on r.id = ura.role_id
                        where ura.user_id = ?::uuid
                          and r.code = ?
                          and ura.event_id is null
                          and ura.organization_id is null
                        """, Integer.class, targetUserId, "organizer");

                org.junit.jupiter.api.Assertions.assertEquals(1, count);
        }

        @Test
        void shouldReturnForbiddenWhenParticipantAssignsRole() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String participantEmail = uniqueEmail("participant");
                createUser(participantEmail, "secret123", "Petr", "Ivanov");
                String participantToken = login(participantEmail, "secret123");

                String payload = """
                        {
                          "roleCode": "organizer"
                        }
                        """;

                mockMvc.perform(post("/api/v1/users/{id}/roles", targetUserId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isForbidden());
        }

        @Test
        void shouldRevokeRoleWhenAdmin() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String adminEmail = uniqueEmail("admin");
                String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
                assignRole(adminUserId, "platform_admin");
                String adminToken = login(adminEmail, "secret123");

                assignRole(targetUserId, "organizer");

                mockMvc.perform(delete("/api/v1/users/{id}/roles/{roleCode}", targetUserId, "organizer")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                        .andExpect(status().isNoContent());

                Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from user_role_assignments ura
                        join roles r on r.id = ura.role_id
                        where ura.user_id = ?::uuid
                          and r.code = ?
                          and ura.event_id is null
                          and ura.organization_id is null
                        """, Integer.class, targetUserId, "organizer");

                org.junit.jupiter.api.Assertions.assertEquals(0, count);
        }

        @Test
        void shouldReturnForbiddenWhenParticipantRevokesRole() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String participantEmail = uniqueEmail("participant");
                createUser(participantEmail, "secret123", "Petr", "Ivanov");
                String participantToken = login(participantEmail, "secret123");

                mockMvc.perform(delete("/api/v1/users/{id}/roles/{roleCode}", targetUserId, "organizer")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken))
                        .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturnNotFoundWhenAssigningUnknownRole() throws Exception {
                String targetEmail = uniqueEmail("target");
                String targetUserId = createUser(targetEmail, "secret123", "Anna", "Smirnova");

                String adminEmail = uniqueEmail("admin");
                String adminUserId = createUser(adminEmail, "secret123", "Admin", "User");
                assignRole(adminUserId, "platform_admin");
                String adminToken = login(adminEmail, "secret123");

                String payload = """
                        {
                        "roleCode": "non_existing_role"
                        }
                        """;

                mockMvc.perform(post("/api/v1/users/{id}/roles", targetUserId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("identity.role_not_found"));
        }

        // ----- helpers -----

        private String createUser(String email, String password, String firstName, String lastName) throws Exception {
                String payload = """
                        {
                          "email": "%s",
                          "password": "%s",
                          "phone": "79991112233",
                          "firstName": "%s",
                          "lastName": "%s",
                          "birthDate": "1998-03-17",
                          "gender": "female",
                          "city": "Moscow",
                          "countryCode": "RU",
                          "clubName": "Wave Club"
                        }
                        """.formatted(email, password, firstName, lastName);

                String response = mockMvc.perform(post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                return JsonPath.read(response, "$.id");
        }

        private String login(String email, String password) throws Exception {
                String payload = """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password);

                String response = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                return JsonPath.read(response, "$.accessToken");
        }

        private void assignRole(String userId, String roleCode) {
                String roleId = jdbcTemplate.queryForObject(
                        "select id::text from roles where code = ?",
                        String.class,
                        roleCode
                );

                jdbcTemplate.update("""
                        insert into user_role_assignments (user_id, role_id, event_id, organization_id, created_at)
                        values (?::uuid, ?::uuid, null, null, now())
                        """, userId, roleId);
        }

        private String uniqueEmail(String prefix) {
                return prefix + "+" + System.nanoTime() + "@example.com";
        }
}