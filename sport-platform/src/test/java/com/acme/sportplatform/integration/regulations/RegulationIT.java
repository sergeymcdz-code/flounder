package com.acme.sportplatform.integration.regulations;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.sportplatform.regulations.api.CreateRegulationTemplateRequest;
import com.acme.sportplatform.regulations.api.CreateRegulationVersionRequest;
import com.acme.sportplatform.regulations.api.RegulationRules;
import com.acme.sportplatform.regulations.api.RegulationSection;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegulationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldCreateRegulationVersion() throws Exception {
        String templateId = createTemplate("vpiterespasat-2026-pool-versioned");

        RegulationRules rules = new RegulationRules(
                List.of(new RegulationSection("general", "Общие положения"))
        );

        CreateRegulationVersionRequest request = new CreateRegulationVersionRequest(
                1,
                rules,
                "Первая редакция",
                LocalDate.parse("2026-09-01")
        );

        mockMvc.perform(post("/api/v1/regulation-templates/{id}/versions", templateId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.regulationTemplateId").value(templateId))
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.notes").value("Первая редакция"))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-09-01"));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldPublishRegulationVersion() throws Exception {
        String templateId = createTemplate("vpiterespasat-2026-publish");
        String versionId = createVersion(templateId, 1);

        mockMvc.perform(post("/api/v1/regulation-versions/{id}/publish", versionId)
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(versionId))
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.versionNo").value(1));
    }

    private String createTemplate(String code) throws Exception {
        CreateRegulationTemplateRequest request = new CreateRegulationTemplateRequest(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                code,
                "ВПИТЕРЕСПАСАТЬ 2026 — бассейн",
                "Регламент соревнований по водно-спасательному многоборью"
        );

        String response = mockMvc.perform(post("/api/v1/regulation-templates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String createVersion(String templateId, int versionNo) throws Exception {
        RegulationRules rules = new RegulationRules(
                List.of(new RegulationSection("general", "Общие положения"))
        );

        CreateRegulationVersionRequest request = new CreateRegulationVersionRequest(
                versionNo,
                rules,
                "Черновик",
                LocalDate.parse("2026-09-01")
        );

        String response = mockMvc.perform(post("/api/v1/regulation-templates/{id}/versions", templateId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }
}