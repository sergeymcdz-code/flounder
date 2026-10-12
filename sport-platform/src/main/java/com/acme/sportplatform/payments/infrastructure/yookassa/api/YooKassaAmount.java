package com.acme.sportplatform.payments.infrastructure.yookassa.api;

public record YooKassaAmount(
        String value,
        String currency
) {
}