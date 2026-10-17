package com.acme.sportplatform.notifications.application;

import com.acme.sportplatform.events.*;
import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.notifications.infrastructure.*;
import com.acme.sportplatform.registrations.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PaymentReminderService {
    private final RegistrationPaymentReminderLookup registrations;
    private final NotificationEventLookup events;
    private final UserLookup users;
    private final NotificationOutboxRepository outbox;
    private final NotificationSettingsRepository settings;
    private final EventLockService locks;
    private final Clock clock;

    public PaymentReminderService(
            RegistrationPaymentReminderLookup registrations,
            NotificationEventLookup events,
            UserLookup users,
            NotificationOutboxRepository outbox,
            NotificationSettingsRepository settings,
            EventLockService locks,
            @Qualifier("notificationClock") Clock clock) {
        this.registrations = registrations;
        this.events = events;
        this.users = users;
        this.outbox = outbox;
        this.settings = settings;
        this.locks = locks;
        this.clock = clock;
    }

    @Transactional
    public void initial(UUID id) {
        send(id, false, clock.instant(), Map.of());
    }

    @Transactional
    public void remindOverdue() {
        Instant now = clock.instant();
        var contexts =
                events.paymentReminderEvents(now).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        NotificationEventLookup.Context::id,
                                        java.util.function.Function.identity()));
        if (contexts.isEmpty()) return;
        for (var id : registrations.pendingIds(contexts.keySet())) send(id, true, now, contexts);
    }

    public static boolean eligible(
            Instant now, Instant dueAt, NotificationOutboxRepository.ReminderHistory history) {
        return now.isAfter(dueAt)
                && history.count() < 3
                && (history.lastAt() == null
                        || !now.isBefore(history.lastAt().plus(Duration.ofDays(3))));
    }

    private void send(
            UUID id,
            boolean repeat,
            Instant now,
            Map<UUID, NotificationEventLookup.Context> contexts) {
        var candidate = registrations.reminder(id);
        if (candidate.isEmpty()) return;
        locks.lockForUpdate(candidate.get().eventId());
        // Recheck after the same event lock used by payment mutations.
        var row = registrations.reminder(id);
        if (row.isEmpty()) return;
        var r = row.get();
        var context =
                repeat
                        ? Optional.ofNullable(contexts.get(r.eventId()))
                        : events.findNotificationContext(r.eventId());
        if (context.isEmpty() || r.userId() == null) return;
        var e = context.get();
        var local = now.atZone(ZoneId.of(e.timezone() == null ? "UTC" : e.timezone()));
        var history = outbox.reminderHistory(id);
        if (repeat
                && (local.getHour() != 9
                        || local.getMinute() >= 10
                        || !eligible(now, r.dueAt().toInstant(), history)
                        || history.lastAt() != null
                                && history.lastAt()
                                        .atZone(local.getZone())
                                        .toLocalDate()
                                        .equals(local.toLocalDate()))) return;
        if (!repeat && history.count() > 0
                || !settings.enabled(e.organizationId(), "payment_reminder")) return;
        users.findById(r.userId())
                .filter(u -> u.email() != null && !u.email().isBlank())
                .ifPresent(
                        u -> {
                            var payload = new HashMap<String, Object>();
                            payload.put("registration_id", id.toString());
                            payload.put("name", u.firstName() == null ? "Участник" : u.firstName());
                            payload.put("event", e.title());
                            payload.put("amount", r.amount().toPlainString());
                            payload.put("currency", r.currency());
                            payload.put("instructions", r.instructions());
                            payload.put(
                                    "dueAt",
                                    r.dueAt()
                                            .atZoneSameInstant(local.getZone())
                                            .format(
                                                    DateTimeFormatter.ofPattern(
                                                            "dd.MM.yyyy HH:mm z")));
                            outbox.enqueue(
                                    e.id(),
                                    u.email(),
                                    "payment_reminder",
                                    payload,
                                    "payment_reminder:" + id + ":" + history.count(),
                                    now);
                        });
    }
}
