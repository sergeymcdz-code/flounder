package com.acme.sportplatform.payments.provider;

import java.time.OffsetDateTime;

import com.acme.sportplatform.payments.domain.PaymentStatus;

public record ProviderPaymentResult(
        String externalPaymentId,
        String confirmationUrl,
        PaymentStatus status,
        OffsetDateTime expiresAt,
        String providerMetadata
) {
}