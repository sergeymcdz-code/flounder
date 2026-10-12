package com.acme.sportplatform.payments.infrastructure.yookassa.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YooKassaConfirmation(
        String type,
        @JsonProperty("return_url")
        String returnUrl
) {
}