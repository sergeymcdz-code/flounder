package com.acme.sportplatform.integration.events;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.acme.sportplatform.events.api.CreateEventRequest;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.api.CreateOrganizationRequest;
import com.acme.sportplatform.organizations.api.CreateVenueRequest;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIntegrationTest
        extends AbstractPostgresIntegrationTest {

    private static final UUID SPORT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001"
            );

    private static final UUID TEST_ADMIN_USER_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001"
            );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldCreateAndPublishEvent() throws Exception {
        UUID organizationId = createOrganization();
        UUID venueId = createVenue();
        UUID regulationVersionId = createRegulationVersion();

        String eventId = createEvent(
                organizationId,
                venueId,
                regulationVersionId,
                "Кубок города по плаванию",
                "Тестовое событие"
        );

        mockMvc.perform(
                        post(
                                "/api/v1/events/{id}/publish",
                                eventId
                        )
                                .with(csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(eventId)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PUBLISHED")
                );

        mockMvc.perform(
                        get(
                                "/api/v1/events/{id}",
                                eventId
                        )
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(eventId)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PUBLISHED")
                );
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldOpenCloseRegistrationAndCompleteEvent()
            throws Exception {

        UUID organizationId = createOrganization();
        UUID venueId = createVenue();
        UUID regulationVersionId = createRegulationVersion();

        String eventId = createEvent(
                organizationId,
                venueId,
                regulationVersionId,
                "Соревнование с регистрацией",
                "Проверка смены статусов"
        );

        mockMvc.perform(
                        post(
                                "/api/v1/events/{id}/publish",
                                eventId
                        )
                                .with(csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("PUBLISHED")
                );

        mockMvc.perform(
                        post(
                                "/api/v1/events/{id}/open-registration",
                                eventId
                        )
                                .with(csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("REGISTRATION_OPEN")
                );

        mockMvc.perform(
                        post(
                                "/api/v1/events/{id}/close-registration",
                                eventId
                        )
                                .with(csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("REGISTRATION_CLOSED")
                );

        mockMvc.perform(
                        post(
                                "/api/v1/events/{id}/complete",
                                eventId
                        )
                                .with(csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("COMPLETED")
                );

        mockMvc.perform(
                        get(
                                "/api/v1/events/{id}",
                                eventId
                        )
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(eventId)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("COMPLETED")
                );
    }

    private UUID createOrganization() throws Exception {
        CreateOrganizationRequest request =
                new CreateOrganizationRequest(
                        "organizer",
                        "Организатор Event IT",
                        null,
                        null,
                        "events-it@example.com",
                        "79990001122"
                );

        String response = mockMvc.perform(
                        post("/api/v1/organizations")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(
                        jsonPath("$.type")
                                .value("organizer")
                )
                .andExpect(
                        jsonPath("$.name")
                                .value("Организатор Event IT")
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String organizationId =
                JsonPath.read(response, "$.id");

        return UUID.fromString(organizationId);
    }

    private UUID createVenue() throws Exception {
        CreateVenueRequest request =
                new CreateVenueRequest(
                        "Бассейн Олимп",
                        "RU",
                        "Санкт-Петербург",
                        "проспект Спортивный, 10",
                        "Europe/Moscow"
                );

        String response = mockMvc.perform(
                        post("/api/v1/venues")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(
                        jsonPath("$.name")
                                .value("Бассейн Олимп")
                )
                .andExpect(
                        jsonPath("$.timezone")
                                .value("Europe/Moscow")
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String venueId =
                JsonPath.read(response, "$.id");

        return UUID.fromString(venueId);
    }

    private String createEvent(
            UUID organizationId,
            UUID venueId,
            UUID regulationVersionId,
            String title,
            String description
    ) throws Exception {

        CreateEventRequest request =
                new CreateEventRequest(
                        organizationId,
                        venueId,
                        SPORT_ID,
                        regulationVersionId,
                        title,
                        description,
                        OffsetDateTime.of(
                                2026,
                                8,
                                1,
                                0,
                                0,
                                0,
                                0,
                                ZoneOffset.UTC
                        ),
                        OffsetDateTime.of(
                                2026,
                                9,
                                20,
                                23,
                                59,
                                59,
                                0,
                                ZoneOffset.UTC
                        ),
                        OffsetDateTime.of(
                                2026,
                                10,
                                1,
                                9,
                                0,
                                0,
                                0,
                                ZoneOffset.UTC
                        ),
                        OffsetDateTime.of(
                                2026,
                                10,
                                2,
                                18,
                                0,
                                0,
                                0,
                                ZoneOffset.UTC
                        )
                );

        String response = mockMvc.perform(
                        post("/api/v1/events")

                                /*
                                 * EventController ожидает principal
                                 * типа AuthenticatedUserPrincipal.
                                 */
                                .with(authentication(
                                        platformAdminAuthentication()
                                ))

                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andDo(print())

                /*
                 * POST /api/v1/events возвращает 201 Created.
                 */
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(
                        jsonPath("$.title")
                                .value(title)
                )
                .andExpect(
                        jsonPath("$.description")
                                .value(description)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("DRAFT")
                )
                .andExpect(
                        jsonPath("$.publicSlug")
                                .exists()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private UUID createRegulationVersion() throws Exception {
        String templateId = createRegulationTemplate();

        String response = mockMvc.perform(
                        post(
                                "/api/v1/regulation-templates"
                                        + "/{id}/versions",
                                templateId
                        )
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "versionNo": 1,
                                          "rulesJson": {
                                            "sections": []
                                          },
                                          "notes": "Регламент для EventIntegrationTest",
                                          "effectiveFrom": "2026-09-01"
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("draft")
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String versionId =
                JsonPath.read(response, "$.id");

        mockMvc.perform(
                        post(
                                "/api/v1/regulation-versions"
                                        + "/{id}/publish",
                                versionId
                        )
                                .with(csrf())
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("published")
                );

        return UUID.fromString(versionId);
    }

    private String createRegulationTemplate()
            throws Exception {

        String code =
                "events-"
                        + UUID.randomUUID()
                                .toString()
                                .substring(0, 8);

        String response = mockMvc.perform(
                        post("/api/v1/regulation-templates")
                                .with(csrf())
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "sportId": "%s",
                                          "code": "%s",
                                          "name": "Регламент Event IT",
                                          "description": "Шаблон регламента для EventIntegrationTest"
                                        }
                                        """.formatted(
                                                SPORT_ID,
                                                code
                                        ))
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private Authentication platformAdminAuthentication() {
        AuthenticatedUserPrincipal principal =
                () -> TEST_ADMIN_USER_ID;

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_PLATFORM_ADMIN"
                        )
                )
        );
    }
}