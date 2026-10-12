package com.acme.sportplatform.integration.sports;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisciplineTemplateIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void shouldReturnDisciplineTemplatesList() throws Exception {
        mockMvc.perform(get("/api/v1/discipline-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldCreateDisciplineTemplate() throws Exception {
        mockMvc.perform(post("/api/v1/discipline-templates")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "sportId": "10000000-0000-0000-0000-000000000002",
                                  "code": "obstacle-swim-200",
                                  "name": "200 м плавание с препятствиями",
                                  "competitionFormat": "individual",
                                  "unitType": "time",
                                  "resultType": "time",
                                  "rankingStrategy": "min_wins"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sportId").value("10000000-0000-0000-0000-000000000002"))
                .andExpect(jsonPath("$.code").value("obstacle-swim-200"))
                .andExpect(jsonPath("$.name").value("200 м плавание с препятствиями"));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldReturnNotFoundWhenSportMissing() throws Exception {
        mockMvc.perform(post("/api/v1/discipline-templates")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "sportId": "20000000-0000-0000-0000-000000000001",
                                  "code": "ghost-discipline",
                                  "name": "Ghost Discipline",
                                  "competitionFormat": "individual",
                                  "unitType": "time",
                                  "resultType": "time",
                                  "rankingStrategy": "min_wins"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldReturnConflictWhenDisciplineTemplateAlreadyExists() throws Exception {
        String payload = """
                {
                  "sportId": "10000000-0000-0000-0000-000000000002",
                  "code": "mannequin-tow-100",
                  "name": "100 м буксировка манекена в ластах",
                  "competitionFormat": "individual",
                  "unitType": "time",
                  "resultType": "time",
                  "rankingStrategy": "min_wins"
                }
                """;

        mockMvc.perform(post("/api/v1/discipline-templates")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/discipline-templates")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }
}