package com.acme.sportplatform.registrations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public interface RegistrationPaymentReminderLookup {
    List<UUID> pendingIds(java.util.Collection<UUID> eventIds);

    Optional<Reminder> reminder(UUID id);

    record Reminder(
            UUID registrationId,
            UUID eventId,
            UUID userId,
            BigDecimal amount,
            String currency,
            String instructions,
            OffsetDateTime dueAt) {}
}
