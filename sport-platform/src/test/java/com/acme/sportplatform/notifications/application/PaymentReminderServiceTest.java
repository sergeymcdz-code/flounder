package com.acme.sportplatform.notifications.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.acme.sportplatform.events.*;
import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.notifications.infrastructure.*;
import com.acme.sportplatform.registrations.RegistrationPaymentReminderLookup;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class PaymentReminderServiceTest {
    @Test
    void venueNineAmAndOutboxHistoryLimitFrequencyToTwoRepeats() {
        var registrations = mock(RegistrationPaymentReminderLookup.class);
        var events = mock(NotificationEventLookup.class);
        var users = mock(UserLookup.class);
        var outbox = mock(NotificationOutboxRepository.class);
        var settings = mock(NotificationSettingsRepository.class);
        UUID registration = UUID.randomUUID(),
                event = UUID.randomUUID(),
                org = UUID.randomUUID(),
                user = UUID.randomUUID();
        var now = new AtomicReference<>(Instant.parse("2026-10-15T06:00:00Z"));
        var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(a -> now.get());
        var history =
                new AtomicReference<>(new NotificationOutboxRepository.ReminderHistory(0, null));
        var context =
                new NotificationEventLookup.Context(
                        event, org, "Кубок", null, "cup", UUID.randomUUID(), "Europe/Moscow");
        when(events.paymentReminderEvents(any())).thenReturn(List.of(context));
        when(registrations.pendingIds(anyCollection())).thenReturn(List.of(registration));
        when(registrations.reminder(registration))
                .thenReturn(
                        Optional.of(
                                new RegistrationPaymentReminderLookup.Reminder(
                                        registration,
                                        event,
                                        user,
                                        new BigDecimal("500"),
                                        "RUB",
                                        "Банк",
                                        OffsetDateTime.parse("2026-10-14T09:00:00+03:00"))));
        when(events.findNotificationContext(event))
                .thenReturn(
                        Optional.of(
                                new NotificationEventLookup.Context(
                                        event,
                                        org,
                                        "Кубок",
                                        null,
                                        "cup",
                                        UUID.randomUUID(),
                                        "Europe/Moscow")));
        when(users.findById(user))
                .thenReturn(
                        Optional.of(
                                new UserLookup.User(user, "test@example.org", "Иван", "Тестов")));
        when(settings.enabled(org, "payment_reminder")).thenReturn(true);
        when(outbox.reminderHistory(registration)).thenAnswer(a -> history.get());
        doAnswer(
                        a -> {
                            history.set(
                                    new NotificationOutboxRepository.ReminderHistory(
                                            history.get().count() + 1, now.get()));
                            return null;
                        })
                .when(outbox)
                .enqueue(
                        eq(event),
                        anyString(),
                        eq("payment_reminder"),
                        anyMap(),
                        anyString(),
                        any());
        var service =
                new PaymentReminderService(
                        registrations,
                        events,
                        users,
                        outbox,
                        settings,
                        mock(EventLockService.class),
                        clock);
        service.initial(registration);
        service.initial(registration);
        service.remindOverdue();
        assertThat(history.get().count()).isEqualTo(1);
        for (int day = 16; day <= 28; day++) {
            now.set(Instant.parse("2026-10-" + day + "T05:00:00Z"));
            service.remindOverdue(); // 08:00 venue time
            now.set(Instant.parse("2026-10-" + day + "T06:00:00Z"));
            service.remindOverdue();
            service.remindOverdue();
        }
        assertThat(history.get().count()).isEqualTo(3);
        verify(outbox, times(3))
                .enqueue(
                        eq(event),
                        anyString(),
                        eq("payment_reminder"),
                        anyMap(),
                        anyString(),
                        any());
    }

    @Test
    void overdueBatchKeepsOneInstantEvenWhenProcessingCrossesTheWindowBoundary() {
        var registrations = mock(RegistrationPaymentReminderLookup.class);
        var events = mock(NotificationEventLookup.class);
        var users = mock(UserLookup.class);
        var outbox = mock(NotificationOutboxRepository.class);
        var settings = mock(NotificationSettingsRepository.class);
        var clock = mock(Clock.class);
        UUID event = UUID.randomUUID(), org = UUID.randomUUID(), user = UUID.randomUUID();
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        var now = Instant.parse("2026-10-17T06:09:59Z");
        when(clock.instant()).thenReturn(now, now.plusSeconds(61));
        when(events.paymentReminderEvents(now))
                .thenReturn(
                        List.of(
                                new NotificationEventLookup.Context(
                                        event,
                                        org,
                                        "Кубок",
                                        null,
                                        "cup",
                                        UUID.randomUUID(),
                                        "Europe/Moscow")));
        when(registrations.pendingIds(anyCollection())).thenReturn(ids);
        for (var id : ids) {
            when(registrations.reminder(id))
                    .thenReturn(
                            Optional.of(
                                    new RegistrationPaymentReminderLookup.Reminder(
                                            id,
                                            event,
                                            user,
                                            new BigDecimal("500"),
                                            "RUB",
                                            "Банк",
                                            OffsetDateTime.parse("2026-10-10T09:00:00+03:00"))));
            when(outbox.reminderHistory(id))
                    .thenReturn(
                            new NotificationOutboxRepository.ReminderHistory(
                                    1, now.minus(Duration.ofDays(4))));
        }
        when(users.findById(user))
                .thenReturn(
                        Optional.of(
                                new UserLookup.User(user, "test@example.org", "Иван", "Тестов")));
        when(settings.enabled(org, "payment_reminder")).thenReturn(true);
        new PaymentReminderService(
                        registrations,
                        events,
                        users,
                        outbox,
                        settings,
                        mock(EventLockService.class),
                        clock)
                .remindOverdue();
        verify(clock).instant();
        verify(outbox, times(2))
                .enqueue(
                        eq(event),
                        anyString(),
                        eq("payment_reminder"),
                        anyMap(),
                        anyString(),
                        eq(now));
        verify(events, never()).findNotificationContext(any());
    }
}
