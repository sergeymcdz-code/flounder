package com.acme.sportplatform.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.*;

class RegistrationPaymentsIntegrationTest extends Qa04SecurityFixture {
    void fee() {
        jdbc.update(
                "update registrations set fee_amount=500,fee_currency='RUB',reviewed_at=now() where"
                        + " id=?",
                id("registration"));
        em.clear();
    }

    void settings(String payment) {
        jdbc.update(
                "update events set settings_json=jsonb_build_object('payment',?::jsonb) where id=?",
                payment,
                id("event"));
        em.clear();
    }

    org.springframework.test.web.servlet.ResultActions action(String action, String body)
            throws Exception {
        var request =
                post("/api/v1/registrations/{id}/" + action, id("registration"))
                        .with(authentication(auth("organizer")));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        var result = mvc.perform(request);
        em.flush();
        return result;
    }

    @Test
    void bodylessMarkPaidKeepsContractAndRejectsDuplicate() throws Exception {
        fee();
        action("mark-paid", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(500))
                .andExpect(jsonPath("$.paymentMethod").value("OTHER"));
        action("mark-paid", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registrations.already_paid"));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from registration_payment_log where"
                                        + " registration_id=?",
                                Integer.class,
                                id("registration")))
                .isEqualTo(1);
    }

    @Test
    void detailsAuditRefundResetWaiverAndAdmission() throws Exception {
        fee();
        jdbc.update("update registrations set status='ADMITTED' where id=?", id("registration"));
        em.clear();
        action(
                        "mark-paid",
                        "{\"amount\":450,\"method\":\"TRANSFER\",\"reference\":\"ПП-42\",\"comment\":\"клуб\",\"paidAt\":\"2026-10-10T10:00:00Z\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"))
                .andExpect(jsonPath("$.paidBy.userId").value(ids.get("organizer")))
                .andExpect(jsonPath("$.paymentReference").value("ПП-42"));
        action("refund-payment", "{\"comment\":\"возврат\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"))
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        action("reset-payment", "{\"comment\":\"ошибка\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAt").doesNotExist())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        action("waive-payment", "{\"comment\":\"льгота\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("WAIVED"));
        mvc.perform(
                        get("/api/v1/registrations/{id}/payment-log", id("registration"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        assertThat(
                        jdbc.queryForObject(
                                "select performed_by from registration_payment_log where"
                                        + " registration_id=? and action='MARK_PAID'",
                                UUID.class,
                                id("registration")))
                .isEqualTo(id("organizer"));
    }

    @Test
    void bulkSkipsForeignAndSummaryGroupsThreeClubs() throws Exception {
        fee();
        for (int i = 1; i <= 2; i++) {
            UUID profile = UUID.randomUUID(), registration = UUID.randomUUID();
            jdbc.update(
                    "insert into profiles(id,first_name,last_name,birth_date,gender) values"
                            + " (?,'Иван','Клуб','2014-01-01','M')",
                    profile);
            jdbc.update(
                    "insert into"
                        + " registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,fee_amount,fee_currency,payment_status,created_at,updated_at)"
                        + " values (?,?,?,?,'CONFIRMED',?::jsonb,500,'RUB','PENDING',now(),now())",
                    registration,
                    id("event"),
                    id("discipline"),
                    profile,
                    "{\"clubName\":\"Клуб " + i + "\"}");
        }
        mvc.perform(
                        post("/api/v1/events/{id}/payments/mark-paid-bulk", id("event"))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "registrationIds",
                                                        List.of(
                                                                id("registration"),
                                                                id("foreignRegistration")),
                                                        "method",
                                                        "CLUB_INVOICE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped[0].id").value(ids.get("foreignRegistration")));
        em.flush();
        mvc.perform(
                        get("/api/v1/events/{id}/payments/summary", id("event"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.due").value(1500))
                .andExpect(jsonPath("$.totals.paid").value(500))
                .andExpect(jsonPath("$.totals.pending").value(1000))
                .andExpect(jsonPath("$.byClub.length()").value(3));
    }

    @Test
    void confirmationFreezesFeeAndEventModeChargesOnlyFirstConfirmation() throws Exception {
        settings(
                "{\"enabled\":true,\"feeMode\":\"PER_EVENT\",\"eventFeeAmount\":900,\"instructions\":\"Банк\",\"currency\":\"RUB\",\"dueDays\":3}");
        jdbc.update("update registrations set status='SUBMITTED' where id=?", id("registration"));
        em.clear();
        action("review", "{\"decision\":\"CONFIRMED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeAmount").value(900));
        ids.put("paymentDiscipline", UUID.randomUUID().toString());
        discipline("paymentDiscipline", "event", "SECOND", false);
        ids.put("second", UUID.randomUUID().toString());
        registration("second", "event", "paymentDiscipline", "profile", "participant");
        jdbc.update("update registrations set status='SUBMITTED' where id=?", id("second"));
        em.clear();
        mvc.perform(
                        post("/api/v1/registrations/{id}/review", id("second"))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeAmount").value(0))
                .andExpect(jsonPath("$.paymentStatus").value("NOT_REQUIRED"));
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notification_outbox where"
                                        + " template_code='payment_reminder' and event_id=?",
                                Integer.class,
                                id("event")))
                .isEqualTo(1);
        mvc.perform(
                        post("/api/v1/registrations/{id}/payments", id("registration"))
                                .with(authentication(auth("participant"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payments.not_required"));
    }

    @Test
    void validationDisabledRecalculationAndArchivedAreEnforced() throws Exception {
        fee();
        action("mark-paid", "{\"amount\":-1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
        settings("{\"enabled\":false}");
        action("mark-paid", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registrations.payment_disabled"));
        jdbc.update("update registrations set fee_amount=null where id=?", id("registration"));
        em.clear();
        mvc.perform(
                        post("/api/v1/events/{id}/payments/recalculate-fees", id("event"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));
        em.flush();
        assertThat(
                        jdbc.queryForObject(
                                "select payment_status from registrations where id=?",
                                String.class,
                                id("registration")))
                .isEqualTo("NOT_REQUIRED");
        jdbc.update("update events set status='ARCHIVED' where id=?", id("event"));
        em.clear();
        action("mark-paid", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("events.archived"));
    }

    @Test
    void waiverEntersPaidAnalyticsFunnel() throws Exception {
        fee();
        action("waive-payment", "{\"comment\":\"льгота\"}").andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/events/{id}/analytics", id("event"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrations.funnel.paid").value(1));
    }

    @Test
    void settingsPreserveOtherKeysAndPublicInstructionsRequireEnabled() throws Exception {
        jdbc.update(
                "update events set settings_json='{\"retained\":true}'::jsonb where id=?",
                id("event"));
        em.clear();
        mvc.perform(
                        patch("/api/v1/events/{id}", id("event"))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"payment\":{\"enabled\":true,\"instructions\":\"Банк,"
                                            + " фамилия и"
                                            + " клуб\",\"feeMode\":\"PER_DISCIPLINE\",\"currency\":\"RUB\",\"dueDays\":3}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.instructions").value("Банк, фамилия и клуб"));
        em.flush();
        assertThat(
                        jdbc.queryForObject(
                                "select settings_json->>'retained' from events where id=?",
                                String.class,
                                id("event")))
                .isEqualTo("true");
        mvc.perform(get(publicUrl("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.instructions").value("Банк, фамилия и клуб"));
    }

    @Test
    void deliveredReminderRetainsOnlyRegistrationIdentifierForDeduplication() throws Exception {
        fee();
        jdbc.update("update registrations set status='SUBMITTED' where id=?", id("registration"));
        em.clear();
        action("review", "{\"decision\":\"CONFIRMED\"}").andExpect(status().isOk());
        UUID notification =
                jdbc.queryForObject(
                        "select id from notification_outbox where event_id=? and"
                                + " template_code='payment_reminder'",
                        UUID.class,
                        id("event"));
        var outbox =
                new com.acme.sportplatform.notifications.infrastructure
                        .NotificationOutboxRepository(jdbc, json);
        outbox.sent(notification, java.time.Instant.now());
        assertThat(outbox.reminderHistory(id("registration")).count()).isEqualTo(1);
        assertThat(
                        json.readTree(
                                        jdbc.queryForObject(
                                                "select payload::text from notification_outbox"
                                                        + " where id=?",
                                                String.class,
                                                notification))
                                .size())
                .isEqualTo(1);
    }

    @Test
    void resetStartsANewPaymentCycleAndDisabledBulkRejectsBeforeChanges() throws Exception {
        fee();
        action("mark-paid", null).andExpect(status().isOk());
        action("refund-payment", "{\"amount\":200,\"comment\":\"частичный возврат\"}")
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/events/{id}/payments/summary", id("event"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.paid").value(300))
                .andExpect(jsonPath("$.totals.refunded").value(200));
        action("reset-payment", "{\"comment\":\"новая отметка\"}").andExpect(status().isOk());
        action("mark-paid", null).andExpect(status().isOk());
        mvc.perform(
                        get("/api/v1/events/{id}/payments/summary", id("event"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.paid").value(500))
                .andExpect(jsonPath("$.totals.refunded").value(0));
        settings("{\"enabled\":false}");
        mvc.perform(
                        post("/api/v1/events/{id}/payments/mark-paid-bulk", id("event"))
                                .with(authentication(auth("organizer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"registrationIds\":[],\"method\":\"CLUB_INVOICE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registrations.payment_disabled"));
    }

    @Test
    void withdrawalDoesNotEraseSettledMoneyOrRefundsFromClubSummary() throws Exception {
        fee();
        action("mark-paid", null).andExpect(status().isOk());
        jdbc.update("update registrations set status='WITHDRAWN' where id=?", id("registration"));
        em.clear();
        mvc.perform(
                        get("/api/v1/events/{id}/payments/summary", id("event"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.paid").value(500));
        action("refund-payment", "{\"comment\":\"возврат после снятия\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mvc.perform(
                        get("/api/v1/events/{id}/payments/summary", id("event"))
                                .with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.paid").value(0))
                .andExpect(jsonPath("$.totals.refunded").value(500))
                .andExpect(jsonPath("$.byClub.length()").value(1));
    }
}
