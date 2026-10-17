package com.acme.sportplatform.registrations.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentLogEntry(
        UUID id,
        String action,
        BigDecimal amount,
        String currency,
        String method,
        String reference,
        String comment,
        UUID performedBy,
        String performedByName,
        OffsetDateTime performedAt) {}
