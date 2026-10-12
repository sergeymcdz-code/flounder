package com.acme.sportplatform.integration.registrations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID SWIMMING_SPORT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private DisciplineTemplateRepository disciplineTemplateRepository;

    @Autowired
    private EventDisciplineRepository eventDisciplineRepository;

    @Test
    void shouldCreateRegistrationForPublishedDiscipline() throws Exception {
        UserEntity user = createParticipantUser();
        createParticipantProfile(user.getId());

        UUID eventId = createEventWithOpenRegistration(user.getId());
        UUID disciplineTemplateId = createDisciplineTemplate();

        UUID eventDisciplineId = createPublishedEventDiscipline(
                eventId,
                disciplineTemplateId,
                BigDecimal.ZERO
        );

        PlatformUserPrincipal principal = new PlatformUserPrincipal(
                user.getId(),
                user.getEmail(),
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT"))
        );

        String requestJson = """
                {
                  "eventDisciplineId": "%s",
                  "registrationMeta": {
                    "emergencyContactPhone": "+79990000000",
                    "comment": "Тестовая заявка"
                  }
                }
                """.formatted(eventDisciplineId);

        String registrationResponse = mockMvc.perform(
                        post("/api/v1/registrations")
                                .with(user(principal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.eventDisciplineId")
                        .value(eventDisciplineId.toString()))
                .andExpect(jsonPath("$.participantUserId")
                        .value(user.getId().toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").exists())
                .andExpect(jsonPath("$.participantSnapshot.firstName")
                        .value("Иван"))
                .andExpect(jsonPath("$.participantSnapshot.lastName")
                        .value("Иванов"))
                .andExpect(jsonPath("$.participantSnapshot.birthDate")
                        .value("2004-05-10"))
                .andExpect(jsonPath("$.participantSnapshot.gender")
                        .value("MALE"))
                .andExpect(jsonPath("$.registrationMeta.comment")
                        .value("Тестовая заявка"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String registrationId = JsonPath.read(registrationResponse, "$.id");

        PlatformUserPrincipal organizerPrincipal = new PlatformUserPrincipal(
                UUID.randomUUID(),
                "organizer@example.com",
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))
        );

        String reviewRequestJson = """
                {
                  "decision": "CONFIRMED",
                  "reviewNote": "Допуск подтверждён"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/registrations/{registrationId}/review",
                                registrationId
                        )
                                .with(user(organizerPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewRequestJson)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registrationId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.reviewNote")
                        .value("Допуск подтверждён"));

        mockMvc.perform(
                        get("/api/v1/registrations/me")
                                .with(user(principal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(registrationId))
                .andExpect(jsonPath("$[0].participantUserId")
                        .value(user.getId().toString()))
                .andExpect(jsonPath("$[0].eventId")
                        .value(eventId.toString()))
                .andExpect(jsonPath("$[0].eventDisciplineId")
                        .value(eventDisciplineId.toString()))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].reviewNote")
                        .value("Допуск подтверждён"));
    }

    @Test
    void shouldCreatePaymentForConfirmedPaidRegistration() throws Exception {
        UserEntity user = createParticipantUser();
        createParticipantProfile(user.getId());

        UUID eventId = createEventWithOpenRegistration(user.getId());
        UUID disciplineTemplateId = createDisciplineTemplate();

        UUID eventDisciplineId = createPublishedEventDiscipline(
                eventId,
                disciplineTemplateId,
                new BigDecimal("1500.00")
        );

        PlatformUserPrincipal participantPrincipal =
                new PlatformUserPrincipal(
                        user.getId(),
                        user.getEmail(),
                        "",
                        "active",
                        List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT"))
                );

        String registrationRequestJson = """
                {
                  "eventDisciplineId": "%s",
                  "registrationMeta": {
                    "emergencyContactPhone": "+79990000000",
                    "comment": "Платная тестовая заявка"
                  }
                }
                """.formatted(eventDisciplineId);

        String registrationResponse = mockMvc.perform(
                        post("/api/v1/registrations")
                                .with(user(participantPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationRequestJson)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String registrationId = JsonPath.read(
                registrationResponse,
                "$.id"
        );

        PlatformUserPrincipal organizerPrincipal =
                new PlatformUserPrincipal(
                        UUID.randomUUID(),
                        "organizer@example.com",
                        "",
                        "active",
                        List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))
                );

        PlatformUserPrincipal operatorPrincipal =
                new PlatformUserPrincipal(
                        UUID.randomUUID(),
                        "operator@example.com",
                        "",
                        "active",
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
                );

        String reviewRequestJson = """
                {
                  "decision": "CONFIRMED",
                  "reviewNote": "Платная заявка подтверждена"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/registrations/{registrationId}/review",
                                registrationId
                        )
                                .with(user(organizerPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewRequestJson)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        String paymentResponse = mockMvc.perform(
                        post(
                                "/api/v1/registrations/{registrationId}/payments",
                                registrationId
                        )
                                .with(user(participantPrincipal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.registrationId").value(registrationId))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.provider").value("MANUAL"))
                .andExpect(jsonPath("$.confirmationUrl").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId = JsonPath.read(paymentResponse, "$.id");

        mockMvc.perform(
                        get(
                                "/api/v1/registrations/{registrationId}/payments",
                                registrationId
                        )
                                .with(user(participantPrincipal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.registrationId").value(registrationId))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.provider").value("MANUAL"))
                .andExpect(jsonPath("$.confirmationUrl").doesNotExist());

        mockMvc.perform(
                        post("/api/v1/payments/{paymentId}/confirm", paymentId)
                                .with(user(organizerPrincipal))
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/v1/payments/{paymentId}/confirm", paymentId)
                                .with(user(operatorPrincipal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.registrationId").value(registrationId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.provider").value("MANUAL"));

        mockMvc.perform(
                        get(
                                "/api/v1/registrations/{registrationId}/payments",
                                registrationId
                        )
                                .with(user(participantPrincipal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.registrationId").value(registrationId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.provider").value("MANUAL"));

        mockMvc.perform(
                        post("/api/v1/payments/{paymentId}/confirm", paymentId)
                                .with(user(operatorPrincipal))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("payments.not_confirmable"));

        mockMvc.perform(
                        post(
                                "/api/v1/registrations/{registrationId}/payments",
                                registrationId
                        )
                                .with(user(participantPrincipal))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("payments.already_exists"));
    }

    @Test
    void shouldListRegistrationsFilteredByStatusAndTrackReviewer() throws Exception {
        UserEntity user = createParticipantUser();
        createParticipantProfile(user.getId());

        UUID eventId = createEventWithOpenRegistration(user.getId());
        UUID disciplineTemplateId = createDisciplineTemplate();

        UUID eventDisciplineId = createPublishedEventDiscipline(
                eventId,
                disciplineTemplateId,
                BigDecimal.ZERO
        );

        PlatformUserPrincipal principal = new PlatformUserPrincipal(
                user.getId(),
                user.getEmail(),
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT"))
        );

        String requestJson = """
                {
                  "eventDisciplineId": "%s",
                  "registrationMeta": {
                    "emergencyContactPhone": "+79990000000",
                    "comment": "Заявка для листинга"
                  }
                }
                """.formatted(eventDisciplineId);

        String registrationResponse = mockMvc.perform(
                        post("/api/v1/registrations")
                                .with(user(principal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String registrationId = JsonPath.read(registrationResponse, "$.id");

        UUID organizerId = UUID.randomUUID();
        PlatformUserPrincipal organizerPrincipal = new PlatformUserPrincipal(
                organizerId,
                "organizer@example.com",
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))
        );

        mockMvc.perform(
                        get("/api/v1/registrations")
                                .param("status", "SUBMITTED")
                                .with(user(organizerPrincipal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(registrationId))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].reviewedByUserId").doesNotExist());

        String reviewRequestJson = """
                {
                  "decision": "CONFIRMED",
                  "reviewNote": "Проверено для листинга"
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/v1/registrations/{registrationId}/review",
                                registrationId
                        )
                                .with(user(organizerPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewRequestJson)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedByUserId").value(organizerId.toString()))
                .andExpect(jsonPath("$.reviewedAt").exists());

        mockMvc.perform(
                        get("/api/v1/registrations")
                                .param("status", "CONFIRMED")
                                .with(user(organizerPrincipal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(registrationId))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].reviewedByUserId").value(organizerId.toString()))
                .andExpect(jsonPath("$[0].reviewedAt").exists());

        mockMvc.perform(
                        get("/api/v1/registrations")
                                .param("status", "SUBMITTED")
                                .with(user(organizerPrincipal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private UserEntity createParticipantUser() {
        UserEntity user = new UserEntity();
        user.setEmail("participant-" + UUID.randomUUID() + "@example.com");
        user.setPhone("+79990000000");
        user.setPasswordHash("test-password-hash");
        user.setStatus("active");

        return userRepository.save(user);
    }

    private void createParticipantProfile(UUID userId) {
        ProfileEntity profile = new ProfileEntity();
        profile.setUserId(userId);
        profile.setFirstName("Иван");
        profile.setLastName("Иванов");
        profile.setMiddleName("Иванович");
        profile.setBirthDate(LocalDate.parse("2004-05-10"));
        profile.setGender("MALE");
        profile.setCountryCode("RU");
        profile.setCity("Санкт-Петербург");
        profile.setClubName("СК Олимп");
        profile.setSportMeta(Map.of(
                "sportRank", "FIRST_CATEGORY"
        ));

        profileRepository.save(profile);
    }

    private UUID createEventWithOpenRegistration(
                UUID createdByUserId
        ) {
        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

        EventEntity event = new EventEntity();

        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(createdByUserId);
        event.setOrganizationId(UUID.randomUUID());
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SWIMMING_SPORT_ID);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Registration IT Event");
        event.setDescription(
                "Event fixture for registration integration test"
        );
        event.setRegistrationOpenAt(now.minusDays(1));
        event.setRegistrationCloseAt(now.plusDays(20));
        event.setEventStartAt(now.plusDays(30));
        event.setEventEndAt(now.plusDays(31));
        event.setStatus(
                EventStatus.REGISTRATION_OPEN.name()
        );
        event.setPublicSlug(
                "registration-it-" + UUID.randomUUID()
        );
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        return eventRepository.save(event).getId();
        }

    private UUID createDisciplineTemplate() {
        DisciplineTemplateEntity template = new DisciplineTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setSportId(SWIMMING_SPORT_ID);
        template.setCode(
                "100m-freestyle-registration-" + UUID.randomUUID()
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

        return disciplineTemplateRepository.save(template).getId();
    }

    private UUID createPublishedEventDiscipline(
            UUID eventId,
            UUID disciplineTemplateId,
            BigDecimal entryFeeAmount
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventDisciplineEntity discipline = new EventDisciplineEntity();
        discipline.setId(UUID.randomUUID());
        discipline.setEventId(eventId);
        discipline.setDisciplineTemplateId(disciplineTemplateId);
        discipline.setCode("100m-freestyle-men");
        discipline.setName("100 м вольный стиль, мужчины");
        discipline.setCompetitionFormat("INDIVIDUAL");
        discipline.setUnitType("HEAT");
        discipline.setResultType("TIME");
        discipline.setRankingStrategy("ASC");
        discipline.setParticipantLimit(40);
        discipline.setEntryFeeAmount(entryFeeAmount);
        discipline.setEntryFeeCurrency("RUB");
        discipline.setStatus(EventDisciplineStatus.PUBLISHED.name());
        discipline.setSettingsJson("""
                {
                  "distanceMeters": 100,
                  "stroke": "FREESTYLE",
                  "gender": "MALE"
                }
                """);
        discipline.setCreatedAt(now);
        discipline.setUpdatedAt(now);

        return eventDisciplineRepository.save(discipline).getId();
    }
}