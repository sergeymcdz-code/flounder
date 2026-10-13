package com.acme.sportplatform.integration.organizations;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@WithMockUser(roles = "PLATFORM_ADMIN")
class VenueIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void venueMetadataSurvivesCreateGetAndList() throws Exception {
        String response = mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Бассейн", "timezone":"Europe/Moscow", "venueMeta":{"lanes":8,"courseLength":25}}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.venueMeta").value(org.hamcrest.Matchers.equalTo(java.util.Map.of("lanes", 8, "courseLength", 25))))
                .andReturn().getResponse().getContentAsString();
        String venueId = JsonPath.read(response, "$.id");
        mockMvc.perform(get("/api/v1/venues/{id}", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueMeta.lanes").value(8))
                .andExpect(jsonPath("$.venueMeta.courseLength").value(25));
        mockMvc.perform(get("/api/v1/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + venueId + "')].venueMeta.lanes").value(org.hamcrest.Matchers.contains(8)));
    }

    @Test
    void omittedVenueMetadataDefaultsToEmptyObject() throws Exception {
        String response = mockMvc.perform(post("/api/v1/venues").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Площадка без метаданных", "timezone":"Europe/Moscow"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.venueMeta").value(anEmptyMap()))
                .andReturn().getResponse().getContentAsString();
        String venueId = JsonPath.read(response, "$.id");
        mockMvc.perform(get("/api/v1/venues/{id}", venueId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.venueMeta").value(anEmptyMap()));
    }

    @Test
    void missingRegistrationReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/registrations/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("registrations.not_found"));
    }
}
