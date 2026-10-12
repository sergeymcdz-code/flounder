package com.acme.sportplatform.payments.infrastructure.yookassa.webhook;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YooKassaWebhookPaymentObject(
        String id,
        String status,
        boolean paid,
        @JsonProperty("expires_at")
        OffsetDateTime expiresAt
) {
}