package com.acme.sportplatform.payments.infrastructure.yookassa.api;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YooKassaPaymentResponse(
        String id,
        String status,
        boolean paid,
        YooKassaConfirmationResponse confirmation,
        @JsonProperty("expires_at")
        OffsetDateTime expiresAt
) {
}