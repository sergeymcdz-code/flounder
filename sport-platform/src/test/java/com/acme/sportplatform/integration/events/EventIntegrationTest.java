package com.acme.sportplatform.integration.events;

import com.acme.sportplatform.events.api.CreateEventRequest;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.organizations.api.CreateOrganizationRequest;
import com.acme.sportplatform.organizations.api.CreateVenueRequest;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
                    "11111111-1111-1111-1111-111111111111"
            );

    @org.junit.jupiter.api.BeforeEach
    void useAdminPrincipalWithAuthorities() {
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var authentication = context.getAuthentication();
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(role -> "ROLE_PLATFORM_ADMIN".equals(role.getAuthority()))) {
            var principal = new com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"), "admin@flounder.demo", "", "active",
                    authentication.getAuthorities());
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities()));
        }
    }

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

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldApproveApplicantAndAllowOrganizerToManageOnlyOwnEvent() throws Exception {
        UUID applicant = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,status) values (?,?,'test','active')", applicant, applicant + "@example.com");
        AuthenticatedUserPrincipal applicantPrincipal = () -> applicant;
        var participant = new UsernamePasswordAuthenticationToken(applicantPrincipal, null,
                List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT")));
        String submitted = mockMvc.perform(post("/api/v1/organization-registration-requests")
                .with(authentication(participant)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationType\":\"organizer\",\"organizationName\":\"Клуб Самары\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(submitted, "$.id");
        mockMvc.perform(post("/api/v1/organization-registration-requests/{id}/approve", requestId)
                .with(authentication(platformAdminAuthentication())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        UUID organizationId = jdbc.queryForObject("select organization_id from organization_members where user_id = ?", UUID.class, applicant);
        org.junit.jupiter.api.Assertions.assertEquals("owner", jdbc.queryForObject(
                "select member_role from organization_members where user_id = ?", String.class, applicant));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "select count(*) from user_role_assignments a join roles r on r.id=a.role_id where a.user_id=? and r.code='organizer'", Integer.class, applicant));
        mockMvc.perform(post("/api/v1/organization-registration-requests/{id}/approve", requestId)
                .with(authentication(platformAdminAuthentication()))).andExpect(status().isConflict());
        UUID venueId = createVenue();
        UUID regulationId = createRegulationVersion();
        var organizer = new UsernamePasswordAuthenticationToken(applicantPrincipal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var request = new CreateEventRequest(organizationId, venueId, SPORT_ID, regulationId,
                "Кубок Самары 2026", null, now, now.plusDays(1), now.plusDays(2), now.plusDays(3));
        String response = mockMvc.perform(post("/api/v1/events").with(authentication(organizer))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.publicSlug").value("kubok-samary-2026"))
                .andReturn().getResponse().getContentAsString();
        String eventId = JsonPath.read(response, "$.id");
        mockMvc.perform(get("/api/v1/registrations").param("eventId", eventId).with(authentication(organizer)))
                .andExpect(status().isOk());
        UUID templateId = UUID.randomUUID();
        jdbc.update("insert into discipline_templates(id,sport_id,code,name,competition_format,unit_type,result_type,ranking_strategy) values (?,? ,?,'50 м','INDIVIDUAL','HEAT','TIME','ASC')", templateId, SPORT_ID, templateId.toString());
        String disciplineBody = "{\"disciplineTemplateId\":\"" + templateId + "\",\"code\":\"50\",\"name\":\"50 м\"}";
        String disciplineResponse = mockMvc.perform(post("/api/v1/events/{id}/disciplines", eventId).with(authentication(organizer))
                .contentType(MediaType.APPLICATION_JSON).content(disciplineBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String disciplineId = JsonPath.read(disciplineResponse, "$.id");
        mockMvc.perform(post("/api/v1/events/{eventId}/disciplines/{disciplineId}/publish", eventId, disciplineId)
                .with(authentication(organizer))).andExpect(status().isOk());
        UUID profileId = UUID.randomUUID();
        jdbc.update("insert into profiles(id,user_id,first_name,last_name,birth_date) values (?,?,'Иван','Иванов','2014-01-01')", profileId, applicant);
        UUID registrationId = UUID.randomUUID();
        jdbc.update("insert into registrations(id,event_id,event_discipline_id,participant_user_id,participant_profile_id,status,participant_snapshot,created_at,updated_at) values (?,?,?,?,?,'CONFIRMED','{}',now(),now())",
                registrationId, UUID.fromString(eventId), UUID.fromString(disciplineId), applicant, profileId);
        String unitResponse = mockMvc.perform(post("/api/v1/competition-units").with(authentication(organizer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"eventDisciplineId\":\"" + disciplineId + "\",\"label\":\"Заплыв 1\",\"sequenceNumber\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String unitId = JsonPath.read(unitResponse, "$.id");

        mockMvc.perform(get("/api/v1/organizations/{id}/members", organizationId).with(authentication(organizer)))
                .andExpect(status().isOk());
        UUID member = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,status) values (?,?,'test','active')", member, member + "@example.com");
        mockMvc.perform(post("/api/v1/organizations/{id}/members", organizationId).with(authentication(organizer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + member + "\",\"memberRole\":\"operator\"}"))
                .andExpect(status().isCreated());
        AuthenticatedUserPrincipal memberPrincipal = () -> member;
        var operator = new UsernamePasswordAuthenticationToken(memberPrincipal, null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        mockMvc.perform(get("/api/v1/registrations").param("eventId", eventId).with(authentication(operator)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/registrations/{id}/mark-paid", registrationId).with(authentication(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(post("/api/v1/registrations/{id}/mark-paid", registrationId).with(authentication(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(post("/api/v1/competition-units/{id}/entries", unitId).with(authentication(organizer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"registrationId\":\"" + registrationId + "\",\"laneOrPosition\":1}"))
                .andExpect(status().isCreated());

        AuthenticatedUserPrincipal outsiderPrincipal = UUID::randomUUID;
        var outsider = new UsernamePasswordAuthenticationToken(outsiderPrincipal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
        mockMvc.perform(post("/api/v1/events/{id}/disciplines", eventId).with(authentication(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"disciplineTemplateId\":\"" + UUID.randomUUID() + "\",\"code\":\"50\",\"name\":\"50 м\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("events.access_denied"));
        mockMvc.perform(get("/api/v1/registrations").param("eventId", eventId).with(authentication(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/registrations").with(authentication(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/registrations").with(authentication(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(registrationId.toString()));
        mockMvc.perform(get("/api/v1/organizer/events").with(authentication(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(eventId));
        mockMvc.perform(get("/api/v1/organizations/{id}/members", organizationId).with(authentication(operator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/registrations/{id}/mark-paid", registrationId).with(authentication(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/competition-units/{id}", unitId).with(authentication(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/competition-units/{id}/publication", unitId).with(authentication(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}"))
                .andExpect(status().isForbidden());
        jdbc.update("update registrations set status='REJECTED' where id=?", registrationId);
        mockMvc.perform(post("/api/v1/registrations/{id}/mark-paid", registrationId).with(authentication(operator)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("registrations.payment_not_applicable"));

    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void shouldRejectRequestAndApproveUsingExistingOrganization() throws Exception {
        UUID applicant = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,status) values (?,?,'test','active')", applicant, applicant + "@example.com");
        UUID requestId = UUID.randomUUID();
        jdbc.update("insert into organization_registration_requests(id,applicant_user_id,organization_type,organization_name,status,created_at,updated_at) values (?,?,'organizer','Клуб','PENDING',now(),now())", requestId, applicant);
        mockMvc.perform(post("/api/v1/organization-registration-requests/{id}/reject", requestId)
                .with(authentication(platformAdminAuthentication())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"Уточните реквизиты\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Уточните реквизиты"));
        UUID secondRequestId = UUID.randomUUID();
        jdbc.update("insert into organization_registration_requests(id,applicant_user_id,organization_type,organization_name,status,created_at,updated_at) values (?,?,'organizer','Клуб','PENDING',now(),now())", secondRequestId, applicant);
        UUID organizationId = createOrganization();
        mockMvc.perform(post("/api/v1/organization-registration-requests/{id}/approve", secondRequestId)
                .with(authentication(platformAdminAuthentication())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":\"" + organizationId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        org.junit.jupiter.api.Assertions.assertEquals(organizationId, jdbc.queryForObject(
                "select organization_id from organization_members where user_id=?", UUID.class, applicant));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void judgeMemberCanReadEventAndDisciplinesButCannotPublish() throws Exception {
        UUID organizationId = createOrganization();
        UUID venueId = createVenue();
        UUID regulationId = createRegulationVersion();
        String eventId = createEvent(organizationId, venueId, regulationId, "Судейский просмотр", "Проверка доступа судьи");
        UUID judgeId = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,status) values (?,?,'test','active')", judgeId, judgeId + "@example.com");
        mockMvc.perform(post("/api/v1/organizations/{id}/members", organizationId)
                .with(authentication(platformAdminAuthentication())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + judgeId + "\",\"memberRole\":\"judge\"}"))
                .andExpect(status().isCreated());
        var principal = new com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal(
                judgeId, "judge@example.com", "", "active", List.of(new SimpleGrantedAuthority("ROLE_JUDGE")));
        var judge = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        mockMvc.perform(get("/api/v1/events/{id}", eventId).with(authentication(judge)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(eventId));
        mockMvc.perform(get("/api/v1/organizer/events").with(authentication(judge)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(eventId));
        mockMvc.perform(get("/api/v1/events/{id}/disciplines", eventId).with(authentication(judge)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/events/{id}/publish", eventId).with(authentication(judge)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/events/{id}/disciplines", eventId).with(authentication(judge))
                .contentType(MediaType.APPLICATION_JSON).content("{\"disciplineTemplateId\":\"" + UUID.randomUUID() + "\",\"code\":\"50\",\"name\":\"50 м\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldExposeOpenApiAndAuthenticateSeedAdmin() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@flounder.demo\",\"password\":\"Demo2026!\"}"))
                .andExpect(status().isOk());
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
                        "Europe/Moscow",
                        java.util.Map.of()
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
                .andExpect(status().isCreated())
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
                new com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal(
                        TEST_ADMIN_USER_ID, "admin@flounder.demo", "", "active",
                        List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));

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