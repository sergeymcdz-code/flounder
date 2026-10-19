package com.acme.sportplatform.integration.payments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.domain.PaymentWebhookProcessingStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventRepository;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.jayway.jsonpath.JsonPath;

@org.springframework.boot.test.context.SpringBootTest(properties = {
        "app.payments.provider=YOOKASSA", "app.payments.yookassa.shop-id=test-shop",
        "app.payments.yookassa.secret-key=test-key"})
@AutoConfigureMockMvc
class YooKassaWebhookIntegrationTest
        extends AbstractPostgresIntegrationTest {

        @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
        private com.acme.sportplatform.payments.infrastructure.yookassa.YooKassaPaymentProviderClient providerClient;

        @org.junit.jupiter.api.BeforeEach
        void providerResponse() {
                org.mockito.Mockito.doAnswer(invocation -> response(invocation.getArgument(0), "succeeded", "1500.00", "RUB"))
                        .when(providerClient).getPayment(org.mockito.ArgumentMatchers.anyString());
        }

        private com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaPaymentResponse response(
                String id, String status, String amount, String currency) {
                return new com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaPaymentResponse(
                        id, status, "succeeded".equals(status),
                        new com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaAmount(amount, currency),
                        null, null);
        }

        private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhookRequest() {
                return post(WEBHOOK_URL).with(request -> {request.setRemoteAddr("185.71.76.1"); return request;});
        }

        private static final UUID SWIMMING_SPORT_ID =
                UUID.fromString("10000000-0000-0000-0000-000000000001");
        
        private static final UUID TEST_EVENT_CREATOR_ID =
                UUID.fromString(
                        "20000000-0000-0000-0000-000000000001"
                );

        private static final String WEBHOOK_URL =
                "/api/v1/payments/webhooks/yookassa";

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

        @Autowired
        private PaymentRepository paymentRepository;

        @Autowired
        private com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository registrationRepository;

        @Autowired
        private PaymentWebhookEventRepository webhookEventRepository;

        @Test
        void shouldAcceptYooKassaSucceededWebhookWithoutAuthentication()
                throws Exception {
                String externalPaymentId = "yookassa-success-"
                        + UUID.randomUUID();

                PaymentEntity payment = createPendingYooKassaPayment(
                        externalPaymentId
                );

                String payload = succeededWebhookPayload(externalPaymentId);

                mockMvc.perform(
                        webhookRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Request-Id", "webhook-test-request")
                                .content(payload)
                ).andExpect(status().isOk());

                PaymentEntity updatedPayment = paymentRepository
                        .findById(payment.getId())
                        .orElseThrow();

                assertEquals(
                        PaymentStatus.SUCCEEDED.name(),
                        updatedPayment.getStatus()
                );
                assertNotNull(updatedPayment.getPaidAt());
                assertEquals("PAID", registrationRepository.findById(payment.getRegistrationId()).orElseThrow().getStatus());
                assertNull(updatedPayment.getCanceledAt());

                PaymentWebhookEventEntity event = webhookEventRepository
                        .findByDeduplicationKey(
                                "verified:" + externalPaymentId + ":succeeded"
                        )
                        .orElseThrow();

                assertEquals(
                        PaymentProvider.YOOKASSA.name(),
                        event.getProvider()
                );
                assertEquals(externalPaymentId, event.getExternalEventId());
                assertEquals(
                        PaymentWebhookProcessingStatus.PROCESSED.name(),
                        event.getProcessingStatus()
                );
                assertNotNull(event.getProcessedAt());
                assertNull(event.getProcessingError());
                assertEquals(payload, event.getPayload());
        }

        @Test
        void shouldRejectMalformedYooKassaWebhookPayload()
                throws Exception {
                mockMvc.perform(
                        webhookRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ invalid-json")
                ).andExpect(status().isBadRequest());
        }

        @Test
        void shouldProcessDuplicateSucceededWebhookOnlyOnce()
                throws Exception {
                String externalPaymentId = "yookassa-duplicate-"
                        + UUID.randomUUID();

                PaymentEntity payment = createPendingYooKassaPayment(
                        externalPaymentId
                );

                String payload = succeededWebhookPayload(externalPaymentId);

                mockMvc.perform(
                        webhookRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                ).andExpect(status().isOk());

                PaymentEntity afterFirstDelivery = paymentRepository
                        .findById(payment.getId())
                        .orElseThrow();

                OffsetDateTime paidAtAfterFirstDelivery =
                        afterFirstDelivery.getPaidAt();

                assertEquals(
                        PaymentStatus.SUCCEEDED.name(),
                        afterFirstDelivery.getStatus()
                );
                assertNotNull(paidAtAfterFirstDelivery);

                mockMvc.perform(
                        webhookRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                ).andExpect(status().isOk());

                PaymentEntity afterSecondDelivery = paymentRepository
                        .findById(payment.getId())
                        .orElseThrow();

                assertEquals(
                        PaymentStatus.SUCCEEDED.name(),
                        afterSecondDelivery.getStatus()
                );
                assertEquals(
                        paidAtAfterFirstDelivery,
                        afterSecondDelivery.getPaidAt()
                );

                PaymentWebhookEventEntity event = webhookEventRepository
                        .findByDeduplicationKey(
                                "verified:" + externalPaymentId + ":succeeded"
                        )
                        .orElseThrow();

                assertEquals(
                        PaymentWebhookProcessingStatus.PROCESSED.name(),
                        event.getProcessingStatus()
                );
        }

        @Test
        void shouldRejectWebhookForUnknownYooKassaPayment()
                throws Exception {
                String externalPaymentId = "yookassa-unknown-"
                        + UUID.randomUUID();

                String payload = succeededWebhookPayload(externalPaymentId);

                mockMvc.perform(
                        webhookRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                ).andExpect(status().isOk());

                PaymentWebhookEventEntity event = webhookEventRepository
                        .findAll().stream().filter(e -> externalPaymentId.equals(e.getExternalEventId()))
                        .findFirst().orElseThrow();

                assertEquals(
                        PaymentWebhookProcessingStatus.REJECTED.name(),
                        event.getProcessingStatus()
                );
                assertNotNull(event.getProcessedAt());
                assertEquals(
                        "Платёж ЮKassa не найден",
                        event.getProcessingError()
                );
        }

        @Test
        void pendingThenVerifiedSucceededProcessesRegistration() throws Exception {
                String id = UUID.randomUUID().toString();
                PaymentEntity payment = createPendingYooKassaPayment(id);
                org.mockito.Mockito.when(providerClient.getPayment(id))
                        .thenReturn(response(id, "pending", "1500.00", "RUB"))
                        .thenReturn(response(id, "succeeded", "1500.0", "RUB"));
                mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON)
                        .content(succeededWebhookPayload(id))).andExpect(status().isOk());
                assertEquals("PENDING", paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
                assertEquals("PENDING", registrationPaymentStatus(payment.getRegistrationId()));
                assertEquals("REJECTED", webhookEventRepository.findAll().stream()
                        .filter(e -> id.equals(e.getExternalEventId())).findFirst().orElseThrow().getProcessingStatus());
                mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON)
                        .content(succeededWebhookPayload(id))).andExpect(status().isOk());
                assertEquals("SUCCEEDED", paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
                assertEquals("PAID", registrationPaymentStatus(payment.getRegistrationId()));
        }

        @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

        private String registrationPaymentStatus(UUID id) {
                return jdbc.queryForObject("select payment_status from registrations where id = ?", String.class, id);
        }

        @Test
        void rejectsAmountAndCurrencyMismatchWithoutConsumingVerifiedKey() throws Exception {
                String id = UUID.randomUUID().toString();
                PaymentEntity payment = createPendingYooKassaPayment(id);
                org.mockito.Mockito.when(providerClient.getPayment(id))
                        .thenReturn(response(id, "succeeded", "1.00", "RUB"))
                        .thenReturn(response(id, "succeeded", "1500.00", "USD"))
                        .thenReturn(response(id, "succeeded", "1500.00", "RUB"));
                for (int i = 0; i < 2; i++) {
                        mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON)
                                .content(succeededWebhookPayload(id))).andExpect(status().isOk());
                        assertEquals("PENDING", registrationPaymentStatus(payment.getRegistrationId()));
                }
                var rejected = webhookEventRepository.findAll().stream()
                        .filter(e -> id.equals(e.getExternalEventId())).toList();
                assertEquals(2, rejected.size());
                org.junit.jupiter.api.Assertions.assertTrue(rejected.stream().allMatch(e -> "REJECTED".equals(e.getProcessingStatus())));
                mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON)
                        .content(succeededWebhookPayload(id))).andExpect(status().isOk());
                assertEquals("PAID", registrationPaymentStatus(payment.getRegistrationId()));
        }

        @Test
        void usesApiCancellationAndStoresOnlyAllowedHeaders() throws Exception {
                String id = UUID.randomUUID().toString();
                PaymentEntity payment = createPendingYooKassaPayment(id);
                org.mockito.Mockito.when(providerClient.getPayment(id)).thenReturn(response(id, "canceled", "1500.00", "RUB"));
                mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON).header("Authorization", "test-secret")
                        .header("Cookie", "session=test-secret").header("User-Agent", "test-agent")
                        .content(succeededWebhookPayload(id))).andExpect(status().isOk());
                assertEquals("CANCELED", paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
                assertEquals("PENDING", registrationPaymentStatus(payment.getRegistrationId()));
                var event = webhookEventRepository.findByDeduplicationKey("verified:" + id + ":canceled").orElseThrow();
                var headers = new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.getHeadersJson());
                assertEquals(3, headers.size());
                assertEquals("test-agent", headers.path("User-Agent").asText());
                assertEquals("185.71.76.1", headers.path("Client-IP").asText());
        }

        @Test
        void rejectsOversizedBodyWithoutPersistence() throws Exception {
                long before = webhookEventRepository.count();
                mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON).content("x".repeat(16385)))
                        .andExpect(status().isPayloadTooLarge());
                assertEquals(before, webhookEventRepository.count());
        }

        @Test
        void rejectsUntrustedIpDespiteForwardedHeader() throws Exception {
                long before = webhookEventRepository.count();
                mockMvc.perform(post(WEBHOOK_URL).header("X-Forwarded-For", "185.71.76.1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isForbidden());
                assertEquals(before, webhookEventRepository.count());
        }

        @Test
        void apiUnavailableDeliveryCanBeRetried() throws Exception {
                String id = UUID.randomUUID().toString();
                PaymentEntity payment = createPendingYooKassaPayment(id);
                org.mockito.Mockito.when(providerClient.getPayment(id))
                        .thenThrow(new com.acme.sportplatform.common.exception.BusinessException(
                                "payments.provider_unavailable", "Платёжный сервис временно недоступен"))
                        .thenReturn(response(id, "succeeded", "1500.00", "RUB"));
                mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON)
                        .content(succeededWebhookPayload(id))).andExpect(status().isServiceUnavailable());
                assertEquals("PENDING", registrationPaymentStatus(payment.getRegistrationId()));
                mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON)
                        .content(succeededWebhookPayload(id))).andExpect(status().isOk());
                assertEquals("PAID", registrationPaymentStatus(payment.getRegistrationId()));
        }

        @Test
        void rejectsMissingOrInconsistentApiPayment() throws Exception {
                String id = UUID.randomUUID().toString();
                PaymentEntity payment = createPendingYooKassaPayment(id);
                org.mockito.Mockito.when(providerClient.getPayment(id))
                        .thenReturn(null)
                        .thenReturn(response("another-id", "succeeded", "1500.00", "RUB"));
                for (int i = 0; i < 2; i++) {
                        mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON)
                                .content(succeededWebhookPayload(id))).andExpect(status().isOk());
                        assertEquals("PENDING", registrationPaymentStatus(payment.getRegistrationId()));
                }
                org.junit.jupiter.api.Assertions.assertTrue(webhookEventRepository.findAll().stream()
                        .filter(e -> id.equals(e.getExternalEventId()))
                        .allMatch(e -> "REJECTED".equals(e.getProcessingStatus())));
        }

        @Test
        void concurrentDeliveriesShareOneVerifiedState() throws Exception {
                String id = UUID.randomUUID().toString();
                PaymentEntity payment = createPendingYooKassaPayment(id);
                String payload = "{\"object\":{\"id\":\"" + id + "\"}}";
                var start = new java.util.concurrent.CountDownLatch(1);
                try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                        java.util.concurrent.Callable<Integer> delivery = () -> {
                                start.await();
                                return mockMvc.perform(webhookRequest().contentType(MediaType.APPLICATION_JSON)
                                        .content(payload)).andReturn().getResponse().getStatus();
                        };
                        var first = executor.submit(delivery);
                        var second = executor.submit(delivery);
                        start.countDown();
                        assertEquals(200, first.get(20, java.util.concurrent.TimeUnit.SECONDS));
                        assertEquals(200, second.get(20, java.util.concurrent.TimeUnit.SECONDS));
                }
                assertEquals("PAID", registrationPaymentStatus(payment.getRegistrationId()));
                assertEquals(1, webhookEventRepository.findAll().stream()
                        .filter(e -> ("verified:" + id + ":succeeded").equals(e.getDeduplicationKey())).count());
        }

        private PaymentEntity createPendingYooKassaPayment(
                String externalPaymentId
        ) throws Exception {
                UUID registrationId = createConfirmedPaidRegistration();

                PaymentEntity payment = new PaymentEntity();
                payment.setId(UUID.randomUUID());
                payment.setRegistrationId(registrationId);
                payment.setAmount(new BigDecimal("1500.00"));
                payment.setCurrency("RUB");
                payment.setStatus(PaymentStatus.PENDING.name());
                payment.setProvider(PaymentProvider.YOOKASSA.name());
                payment.setExternalPaymentId(externalPaymentId);
                payment.setIdempotencyKey(UUID.randomUUID());
                payment.setConfirmationUrl(
                        "https://yookassa.example/confirmation"
                );
                payment.setProviderMetadata(
                        "{\"yookassaStatus\":\"pending\"}"
                );
                payment.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                payment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

                return paymentRepository.saveAndFlush(payment);
        }

        private UUID createConfirmedPaidRegistration()
                throws Exception {
                UserEntity participant = createParticipantUser();
                createParticipantProfile(participant.getId());

                UUID eventId = createEventWithOpenRegistration();
                UUID disciplineTemplateId = createDisciplineTemplate();

                UUID eventDisciplineId = createPublishedEventDiscipline(
                        eventId,
                        disciplineTemplateId,
                        new BigDecimal("1500.00")
                );

                PlatformUserPrincipal participantPrincipal =
                        new PlatformUserPrincipal(
                                participant.getId(),
                                participant.getEmail(),
                                "",
                                "active",
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_PARTICIPANT"
                                        )
                                )
                        );

                String registrationRequestJson = """
                        {
                        "eventDisciplineId": "%s",
                        "registrationMeta": {
                        "emergencyContactPhone": "+79990000000",
                        "comment": "Webhook payment fixture"
                        }
                        }
                        """.formatted(eventDisciplineId);

                String registrationResponse = mockMvc.perform(
                        post("/api/v1/registrations")
                                .with(user(participantPrincipal))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationRequestJson)
                ).andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                String registrationId = JsonPath.read(
                        registrationResponse,
                        "$.id"
                );

                PlatformUserPrincipal organizerPrincipal =
                        new PlatformUserPrincipal(
                                TEST_EVENT_CREATOR_ID,
                                "organizer-webhook@example.com",
                                "",
                                "active",
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_ORGANIZER"
                                        )
                                )
                        );

                String reviewRequestJson = """
                        {
                        "decision": "CONFIRMED",
                        "reviewNote": "Webhook payment registration confirmed"
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
                ).andExpect(status().isOk());

                return UUID.fromString(registrationId);
        }

        private UserEntity createParticipantUser() {
                UserEntity user = new UserEntity();
                user.setEmail(
                        "webhook-participant-"
                                + UUID.randomUUID()
                                + "@example.com"
                );
                user.setPhone("+79990000000");
                user.setPasswordHash("test-password-hash");
                user.setStatus("active");

                return userRepository.saveAndFlush(user);
        }

        private void createParticipantProfile(UUID userId) {
                ProfileEntity profile = new ProfileEntity();
                profile.setUserId(userId);
                profile.setFirstName("Webhook");
                profile.setLastName("Participant");
                profile.setMiddleName("Test");
                profile.setBirthDate(LocalDate.parse("2000-01-01"));
                profile.setGender("MALE");
                profile.setCountryCode("RU");
                profile.setCity("Санкт-Петербург");
                profile.setClubName("Webhook test club");
                profile.setSportMeta(Map.of(
                        "sportRank",
                        "FIRST_CATEGORY"
                ));

                profileRepository.saveAndFlush(profile);
        }

        private UUID createEventWithOpenRegistration() {
                OffsetDateTime now =
                        OffsetDateTime.now(ZoneOffset.UTC);

                EventEntity event = new EventEntity();

                event.setId(UUID.randomUUID());
                event.setCreatedByUserId(TEST_EVENT_CREATOR_ID);
                event.setOrganizationId(UUID.randomUUID());
                event.setVenueId(UUID.randomUUID());
                event.setSportId(SWIMMING_SPORT_ID);
                event.setRegulationVersionId(UUID.randomUUID());
                event.setTitle("YooKassa webhook event");
                event.setDescription(
                        "Event fixture for YooKassa webhook integration test"
                );
                event.setRegistrationOpenAt(now.minusDays(1));
                event.setRegistrationCloseAt(now.plusDays(20));
                event.setEventStartAt(now.plusDays(30));
                event.setEventEndAt(now.plusDays(31));
                event.setStatus(
                        EventStatus.REGISTRATION_OPEN.name()
                );
                event.setPublicSlug(
                        "yookassa-webhook-" + UUID.randomUUID()
                );
                event.setSettingsJson("{}");
                event.setCreatedAt(now);
                event.setUpdatedAt(now);

                return eventRepository
                        .saveAndFlush(event)
                        .getId();
                }

        private UUID createDisciplineTemplate() {
                DisciplineTemplateEntity template =
                        new DisciplineTemplateEntity();

                template.setId(UUID.randomUUID());
                template.setSportId(SWIMMING_SPORT_ID);
                template.setCode(
                        "yookassa-webhook-discipline-"
                                + UUID.randomUUID()
                );
                template.setName("Webhook test discipline");
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
                        .saveAndFlush(template)
                        .getId();
        }

        private UUID createPublishedEventDiscipline(
                UUID eventId,
                UUID disciplineTemplateId,
                BigDecimal entryFeeAmount
        ) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                EventDisciplineEntity discipline =
                        new EventDisciplineEntity();

                discipline.setId(UUID.randomUUID());
                discipline.setEventId(eventId);
                discipline.setDisciplineTemplateId(disciplineTemplateId);
                discipline.setCode(
                        "yookassa-webhook-100m-"
                                + UUID.randomUUID()
                );
                discipline.setName("Webhook test paid discipline");
                discipline.setCompetitionFormat("INDIVIDUAL");
                discipline.setUnitType("HEAT");
                discipline.setResultType("TIME");
                discipline.setRankingStrategy("ASC");
                discipline.setParticipantLimit(100);
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

                return eventDisciplineRepository
                        .saveAndFlush(discipline)
                        .getId();
        }

        private String succeededWebhookPayload(
                String externalPaymentId
        ) {
                return """
                        {
                        "type": "notification",
                        "event": "payment.succeeded",
                        "object": {
                        "id": "%s",
                        "status": "succeeded",
                        "paid": true
                        }
                        }
                        """.formatted(externalPaymentId);
        }
}