package com.acme.sportplatform.registrations.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PaymentSummary(String currency, Totals totals, List<Club> byClub) {
    public record Totals(
            BigDecimal due,
            BigDecimal paid,
            BigDecimal pending,
            BigDecimal waived,
            BigDecimal refunded) {}

    public record Club(
            String club,
            long participants,
            long registrations,
            BigDecimal due,
            BigDecimal paid,
            BigDecimal pending,
            List<UUID> pendingRegistrationIds) {}
}
