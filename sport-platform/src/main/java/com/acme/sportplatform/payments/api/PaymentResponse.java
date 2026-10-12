package com.acme.sportplatform.payments.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID registrationId,
        BigDecimal amount,
        String currency,
        String status,
        String provider,
        String confirmationUrl,
        OffsetDateTime expiresAt,
        OffsetDateTime paidAt,
        OffsetDateTime canceledAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}