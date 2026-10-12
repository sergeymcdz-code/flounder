package com.acme.sportplatform.payments.infrastructure.yookassa.api;

public record YooKassaCreatePaymentRequest(
        YooKassaAmount amount,
        boolean capture,
        YooKassaConfirmation confirmation,
        String description,
        YooKassaMetadata metadata
) {
}