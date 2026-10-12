package com.acme.sportplatform.integration.competition;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompetitionIntegrationTest
        extends AbstractPostgresIntegrationTest {

    private static final UUID SWIMMING_SPORT_ID =
            UUID.fromString(
                    "10000000-0000-0000-0000-000000000001"
            );

    private static final UUID TEST_CREATOR_USER_ID =
            UUID.fromString(
                    "20000000-0000-0000-0000-000000000001"
            );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private DisciplineTemplateRepository disciplineTemplateRepository;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldCreateAndListEventDiscipline() throws Exception {
        UUID eventId = createDraftEvent();
        UUID disciplineTemplateId = createDisciplineTemplate();

        String requestJson = """
                {
                  "disciplineTemplateId": "%s",
                  "code": "100m-freestyle-men",
                  "name": "100 м вольный стиль, мужчины",
                  "participantLimit": 40,
                  "entryFeeAmount": 1500.00,
                  "entryFeeCurrency": "RUB",
                  "settingsJson": {
                    "distanceMeters": 100,
                    "stroke": "FREESTYLE",
                    "poolLengthMeters": 25,
                    "gender": "MALE",
                    "minAge": 18,
                    "maxAge": 39
                  }
                }
                """.formatted(disciplineTemplateId);

        String response = mockMvc.perform(
                        post(
                                "/api/v1/events/{eventId}/disciplines",
                                eventId
                        )
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.eventId")
                                .value(eventId.toString())
                )
                .andExpect(
                        jsonPath("$.disciplineTemplateId")
                                .value(disciplineTemplateId.toString())
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("100m-freestyle-men")
                )
                .andExpect(
                        jsonPath("$.name")
                                .value(
                                        "100 м вольный стиль, мужчины"
                                )
                )
                .andExpect(
                        jsonPath("$.competitionFormat")
                                .value("INDIVIDUAL")
                )
                .andExpect(
                        jsonPath("$.unitType")
                                .value("HEAT")
                )
                .andExpect(
                        jsonPath("$.resultType")
                                .value("TIME")
                )
                .andExpect(
                        jsonPath("$.rankingStrategy")
                                .value("ASC")
                )
                .andExpect(
                        jsonPath("$.participantLimit")
                                .value(40)
                )
                .andExpect(
                        jsonPath("$.entryFeeAmount")
                                .value(1500.00)
                )
                .andExpect(
                        jsonPath("$.entryFeeCurrency")
                                .value("RUB")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("DRAFT")
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        String eventDisciplineId =
                JsonPath.read(response, "$.id");

        mockMvc.perform(
                        post(
                                "/api/v1/events/{eventId}"
                                        + "/disciplines/{disciplineId}"
                                        + "/publish",
                                eventId,
                                eventDisciplineId
                        )
                                .with(csrf())
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(eventDisciplineId)
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PUBLISHED")
                )
                .andExpect(
                        jsonPath("$.entryFeeAmount")
                                .value(1500.00)
                )
                .andExpect(
                        jsonPath("$.entryFeeCurrency")
                                .value("RUB")
                );

        mockMvc.perform(
                        get(
                                "/api/v1/events/{eventId}/disciplines",
                                eventId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].id")
                                .value(eventDisciplineId)
                )
                .andExpect(
                        jsonPath("$[0].eventId")
                                .value(eventId.toString())
                )
                .andExpect(
                        jsonPath("$[0].code")
                                .value("100m-freestyle-men")
                )
                .andExpect(
                        jsonPath("$[0].entryFeeAmount")
                                .value(1500.00)
                )
                .andExpect(
                        jsonPath("$[0].entryFeeCurrency")
                                .value("RUB")
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .value("PUBLISHED")
                );
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldRejectNegativeEntryFee() throws Exception {
        UUID eventId = createDraftEvent();
        UUID disciplineTemplateId = createDisciplineTemplate();

        String requestJson = """
                {
                  "disciplineTemplateId": "%s",
                  "code": "100m-freestyle-negative-fee",
                  "name": "Некорректная платная дисциплина",
                  "participantLimit": 40,
                  "entryFeeAmount": -1.00,
                  "entryFeeCurrency": "RUB"
                }
                """.formatted(disciplineTemplateId);

        mockMvc.perform(
                        post(
                                "/api/v1/events/{eventId}/disciplines",
                                eventId
                        )
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isBadRequest());
    }

    private UUID createDraftEvent() {
        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

        EventEntity event = new EventEntity();

        event.setId(UUID.randomUUID());

        /*
         * Обязательное поле events.created_by_user_id.
         * @WithMockUser это поле автоматически не заполняет.
         */
        event.setCreatedByUserId(TEST_CREATOR_USER_ID);

        event.setOrganizationId(UUID.randomUUID());
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SWIMMING_SPORT_ID);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Competition IT Event");
        event.setDescription(
                "Event fixture for competition integration test"
        );
        event.setRegistrationOpenAt(now.plusDays(1));
        event.setRegistrationCloseAt(now.plusDays(20));
        event.setEventStartAt(now.plusDays(30));
        event.setEventEndAt(now.plusDays(31));
        event.setStatus(EventStatus.DRAFT.name());
        event.setPublicSlug(
                "competition-it-" + UUID.randomUUID()
        );
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        return eventRepository.save(event).getId();
    }

    private UUID createDisciplineTemplate() {
        DisciplineTemplateEntity template =
                new DisciplineTemplateEntity();

        template.setId(UUID.randomUUID());
        template.setSportId(SWIMMING_SPORT_ID);
        template.setCode(
                "100m-freestyle-" + UUID.randomUUID()
        );
        template.setName("100 м вольный стиль");
        template.setCompetitionFormat("INDIVIDUAL");
        template.setUnitType("HEAT");
        template.setResultType("TIME");
        template.setRankingStrategy("ASC");
        template.setDefaultMeta("""
                {
                  "distanceMeters": 100,
                  "stroke": "FREESTYLE",
                  "poolLengthMeters": 25
                }
                """);

        return disciplineTemplateRepository
                .save(template)
                .getId();
    }
}