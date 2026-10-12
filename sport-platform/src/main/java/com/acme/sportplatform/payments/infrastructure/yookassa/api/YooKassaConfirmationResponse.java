package com.acme.sportplatform.payments.infrastructure.yookassa.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record YooKassaConfirmationResponse(
        String type,
        @JsonProperty("confirmation_url")
        String confirmationUrl
) {
}