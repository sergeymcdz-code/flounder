package com.acme.sportplatform.payments.provider;

import java.math.BigDecimal;
import java.util.UUID;

public record ProviderPaymentCommand(
        UUID paymentId,
        UUID registrationId,
        BigDecimal amount,
        String currency,
        UUID idempotencyKey
) {
}