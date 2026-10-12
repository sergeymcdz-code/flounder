package com.acme.sportplatform.integration.sports;

import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SportsIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldReturnSeededSports() throws Exception {
        mockMvc.perform(get("/api/v1/sports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].isActive").exists());
    }

    @Test
    void shouldReturn401WhenCreateSportWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/sports")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "boxing",
                                  "name": "Boxing"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldCreateSportWhenPlatformAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/sports")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "boxing",
                                  "name": "Boxing"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value("boxing"))
                .andExpect(jsonPath("$.name").value("Boxing"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldReturnConflictWhenSportCodeAlreadyExists() throws Exception {
        mockMvc.perform(post("/api/v1/sports")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "duplicate-sport",
                                  "name": "Duplicate Sport"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sports")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "duplicate-sport",
                                  "name": "Duplicate Sport 2"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldReturnValidationErrorForInvalidSportRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sports")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "",
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}