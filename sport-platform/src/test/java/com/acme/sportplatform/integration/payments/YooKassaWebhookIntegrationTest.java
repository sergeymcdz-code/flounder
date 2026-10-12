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

@AutoConfigureMockMvc
class YooKassaWebhookIntegrationTest
        extends AbstractPostgresIntegrationTest {

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
                        post(WEBHOOK_URL)
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
                assertNull(updatedPayment.getCanceledAt());

                PaymentWebhookEventEntity event = webhookEventRepository
                        .findByDeduplicationKey(
                                "payment.succeeded:" + externalPaymentId
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
                        post(WEBHOOK_URL)
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
                        post(WEBHOOK_URL)
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
                        post(WEBHOOK_URL)
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
                                "payment.succeeded:" + externalPaymentId
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
                        post(WEBHOOK_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                ).andExpect(status().isOk());

                PaymentWebhookEventEntity event = webhookEventRepository
                        .findByDeduplicationKey(
                                "payment.succeeded:" + externalPaymentId
                        )
                        .orElseThrow();

                assertEquals(
                        PaymentWebhookProcessingStatus.REJECTED.name(),
                        event.getProcessingStatus()
                );
                assertNotNull(event.getProcessedAt());
                assertEquals(
                        "Payment not found for YooKassa external payment ID",
                        event.getProcessingError()
                );
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
                                UUID.randomUUID(),
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