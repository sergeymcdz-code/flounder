package com.acme.sportplatform.payments.infrastructure.yookassa.webhook;

public record YooKassaWebhookPayload(
        String type,
        String event,
        YooKassaWebhookPaymentObject object
) {
}