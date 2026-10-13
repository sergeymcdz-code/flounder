package com.acme.sportplatform.security;

import com.acme.sportplatform.common.MailSender;
import com.acme.sportplatform.notifications.application.NotificationWorker;
import com.acme.sportplatform.notifications.infrastructure.*;
import com.acme.sportplatform.registrations.*;
import com.acme.sportplatform.registrations.api.*;
import com.acme.sportplatform.registrations.application.*;
import com.acme.sportplatform.registrations.domain.RegistrationReviewDecision;
import com.acme.sportplatform.results.ResultsPublishedEvent;
import com.acme.sportplatform.results.application.PublishCompetitionUnitUseCase;
import com.acme.sportplatform.scheduling.StartListPublishedEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"app.mail.enabled=true", "app.mail.worker-enabled=false"})
class NotificationIntegrationTest extends Qa04SecurityFixture {
    @Autowired ApplicationEventPublisher publisher;
    @Autowired NotificationOutboxRepository outbox;
    @Autowired NotificationWorker worker;
    @Autowired MailSender mail;
    @Autowired ReviewRegistrationUseCase review;
    @Autowired CreateRegistrationUseCase create;
    @Autowired PublishCompetitionUnitUseCase publishResults;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean(name = "notificationJavaMailSender") JavaMailSender sender;
    @MockitoBean(name = "notificationClock") Clock clock;
    Instant now;
    @BeforeEach void mailFixture() {
        now = Instant.now();
        when(clock.instant()).thenAnswer(i -> now);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }
    long count() { return jdbc.queryForObject("select count(*) from notification_outbox where event_id=?", Long.class, id("event")); }
    void confirmed() { publisher.publishEvent(new RegistrationConfirmedEvent(id("registration"))); }
    void pendingReview() { jdbc.update("update registrations set status='SUBMITTED' where id=?", id("registration")); }

    @Test void domainCreationAndReviewPublishRealEvents() {
        create.execute(id("participant"), new CreateRegistrationRequest(id("newDiscipline"), null, 32150L, null));
        pendingReview();
        review.execute(id("registration"), id("organizer"), new ReviewRegistrationRequest(RegistrationReviewDecision.CONFIRMED, null));
        assertThat(jdbc.queryForList("select template_code from notification_outbox where event_id=?", String.class, id("event")))
                .containsExactlyInAnyOrder("registration_submitted", "registration_confirmed");
    }
    @Test void rejectionIncludesReasonAndParticipantName() {
        pendingReview();
        review.execute(id("registration"), id("organizer"), new ReviewRegistrationRequest(RegistrationReviewDecision.REJECTED, "Нет допуска"));
        String payload = jdbc.queryForObject("select payload::text from notification_outbox where event_id=?", String.class, id("event"));
        assertThat(payload).contains("Нет допуска", "Иван", "QA04 кубок");
    }
    @Test void outboxAndDomainChangeRollBackTogether() {
        pendingReview();
        TestTransaction.flagForCommit(); TestTransaction.end();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            review.execute(id("registration"), id("organizer"), new ReviewRegistrationRequest(RegistrationReviewDecision.CONFIRMED, null));
            assertThat(count()).isEqualTo(1);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("select status from registrations where id=?", String.class, id("registration"))).isEqualTo("SUBMITTED");
        TestTransaction.start();
    }
    @Test void disabledTemplateProducesNoOutboxAndDefaultsEnableOthers() {
        jdbc.update("insert into organization_notification_settings values (?, 'registration_confirmed', false)", id("org"));
        confirmed();
        assertThat(count()).isZero();
        publisher.publishEvent(new RegistrationRejectedEvent(id("registration"), "Нет допуска"));
        assertThat(count()).isEqualTo(1);
    }
    @Test void loggingDeliveryKeepsOutboxWithoutSmtp() {
        confirmed();
        var loggingTransport = new MailTransport(new MailProperties("", 587, "", "", "noreply@localhost", false), sender, new LoggingMailSender());
        new NotificationWorker(outbox, new MailTemplateRenderer(), loggingTransport, clock).sendBatch();
        verifyNoInteractions(sender);
        assertThat(jdbc.queryForObject("select status from notification_outbox where event_id=?", String.class, id("event"))).isEqualTo("SENT");
    }
    @Test void unavailableFutureM06EventsNeedNoListenerOrStartupDependency() {
        record EventRegistrationClosedEvent(UUID eventId, Instant closedAt) {}
        publisher.publishEvent(new EventRegistrationClosedEvent(id("event"), now));
        assertThat(count()).isZero();
    }
    @Test void missingRegistrationOrAccountIsSkipped() {
        publisher.publishEvent(new RegistrationConfirmedEvent(UUID.randomUUID()));
        jdbc.update("update registrations set participant_user_id=null where id=?", id("registration"));
        jdbc.update("update profiles set user_id=null where id=?", id("profile"));
        confirmed();
        assertThat(count()).isZero();
    }
    @Test void startListDeduplicatesAccountAndReplayAndSkipsWithdrawn() {
        var event = new StartListPublishedEvent(UUID.randomUUID(), id("event"), null);
        publisher.publishEvent(event); publisher.publishEvent(event);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select payload->>'link' from notification_outbox where event_id=?", String.class, id("event")))
                .endsWith("/e/" + ids.get("slug") + "/start-list");
        jdbc.update("update registrations set status='WITHDRAWN' where id=?", id("registration"));
        em.clear();
        publisher.publishEvent(new StartListPublishedEvent(UUID.randomUUID(), id("event"), null));
        assertThat(count()).isEqualTo(1);
    }
    @Test void publicationOfResultsQueuesOnlyDisciplineRecipients() {
        jdbc.update("update competition_units set status='PUBLISHED' where id=?", id("unit"));
        publishResults.execute(id("unit"), true);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select template_code from notification_outbox where event_id=?", String.class, id("event")))
                .isEqualTo("results_published");
        publisher.publishEvent(new ResultsPublishedEvent(UUID.randomUUID(), UUID.randomUUID()));
        assertThat(count()).isEqualTo(1);
    }
    @Test void workerSendsUtf8MultipartThroughJavaMailSender() throws Exception {
        confirmed(); worker.sendBatch();
        var message = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getSubject()).isEqualTo("Заявка подтверждена");
        var multipart = (jakarta.mail.Multipart) message.getValue().getContent();
        String serialized;
        try (var output = new java.io.ByteArrayOutputStream()) { message.getValue().writeTo(output); serialized = output.toString(java.nio.charset.StandardCharsets.UTF_8); }
        assertThat(serialized).contains("multipart/alternative", "charset=UTF-8");
        assertThat(multipart.getCount()).isPositive();
        assertThat(jdbc.queryForMap("select status,attempts,sent_at,last_error from notification_outbox where event_id=?", id("event")))
                .containsEntry("status", "SENT").containsEntry("attempts", 1).containsEntry("last_error", null);
    }
    @Test void retriesBackOffAndFailOnFifthAttemptWithoutLeakingSmtpError() {
        confirmed();
        doThrow(new MailSendException("secret-reset-token mail body password")).when(sender).send(any(MimeMessage.class));
        for (int attempt = 1; attempt <= 5; attempt++) {
            worker.sendBatch();
            assertThat(jdbc.queryForObject("select attempts from notification_outbox where event_id=?", Integer.class, id("event"))).isEqualTo(attempt);
            worker.sendBatch();
            verify(sender, times(attempt)).send(any(MimeMessage.class));
            now = now.plusSeconds(10L * (1L << (attempt - 1)));
        }
        worker.sendBatch(); verify(sender, times(5)).send(any(MimeMessage.class));
        assertThat(jdbc.queryForObject("select status from notification_outbox where event_id=?", String.class, id("event"))).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_error from notification_outbox where event_id=?", String.class, id("event"))).doesNotContain("secret", "password", "body");
    }
    @Test void workerBatchIsLimitedToFiftyAndContinuesAfterFailure() {
        for (int n = 0; n < 51; n++) mail.send("batch@example.org", "Восстановление пароля Flounder", "Тестовое письмо " + n);
        doThrow(new MailSendException("test")).doNothing().when(sender).send(any(MimeMessage.class));
        worker.sendBatch(); verify(sender, times(50)).send(any(MimeMessage.class));
        assertThat(jdbc.queryForObject("select count(*) from notification_outbox where status='SENT' and recipient_email='batch@example.org'", Long.class)).isEqualTo(49);
        worker.sendBatch(); verify(sender, times(51)).send(any(MimeMessage.class));
    }
    @Test void concurrentWorkersSkipLockedRows() throws Exception {
        confirmed(); TestTransaction.flagForCommit(); TestTransaction.end();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); return null; })
                .when(sender).send(any(MimeMessage.class));
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(worker::sendBatch);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            worker.sendBatch(); verify(sender, times(1)).send(any(MimeMessage.class));
            release.countDown(); first.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select status from notification_outbox where event_id=?", String.class, id("event"))).isEqualTo("SENT");
        } finally { release.countDown(); executor.shutdownNow(); TestTransaction.start(); }
    }
    @Test void ownerCanChangeSettingsButOrdinaryOrganizerCannot() throws Exception {
        mvc.perform(get("/api/v1/organizations/" + id("org") + "/notification-settings").with(authentication(auth("organizer"))))
                .andExpect(status().isForbidden());
        jdbc.update("update organization_members set member_role='owner' where organization_id=? and user_id=?", id("org"), id("organizer"));
        em.clear();
        mvc.perform(put("/api/v1/organizations/" + id("org") + "/notification-settings").with(authentication(auth("organizer")))
                .contentType("application/json").content("[{\"templateCode\":\"registration_confirmed\",\"enabled\":false}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].enabled").value(false));
        confirmed(); assertThat(count()).isZero();
        jdbc.update("update organization_members set member_role='admin' where organization_id=? and user_id=?", id("org"), id("organizer"));
        em.clear();
        mvc.perform(get("/api/v1/organizations/" + id("org") + "/notification-settings").with(authentication(auth("organizer"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
    }
    @Test void invalidSettingsAreAtomicAndResetCannotBeDisabled() throws Exception {
        String path = "/api/v1/organizations/" + id("org") + "/notification-settings";
        for (String body : List.of("[null]", "[{\"templateCode\":\"registration_confirmed\",\"enabled\":null}]", "[{\"templateCode\":\"password_reset\",\"enabled\":false}]",
                "[{\"templateCode\":\"registration_confirmed\",\"enabled\":false},{\"templateCode\":\"bad\",\"enabled\":false}]",
                "[{\"templateCode\":\"registration_confirmed\",\"enabled\":false},{\"templateCode\":\"registration_confirmed\",\"enabled\":true}]")) {
            mvc.perform(put(path).with(authentication(auth("platform_admin"))).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from organization_notification_settings where organization_id=?", Long.class, id("org"))).isZero();
    }
    @Test void journalChecksScopeFiltersAndNeverReturnsPayload() throws Exception {
        confirmed();
        String path = "/api/v1/events/" + id("event") + "/notifications";
        mvc.perform(get(path).with(authentication(auth("foreign_organizer")))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(authentication(auth("organizer")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].payload").doesNotExist()).andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(path).param("status", "SENT").with(authentication(auth("organizer")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        for (String suffix : List.of("?status=BOGUS", "?page=-1"))
            mvc.perform(get(path + suffix).with(authentication(auth("organizer")))).andExpect(status().isBadRequest());
    }
}
