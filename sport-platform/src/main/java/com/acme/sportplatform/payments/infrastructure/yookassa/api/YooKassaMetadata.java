package com.acme.sportplatform.payments.infrastructure.yookassa.api;

public record YooKassaMetadata(
        String paymentId,
        String registrationId
) {
}