package com.acme.sportplatform.events;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record EventPaymentSettings(
        boolean enabled,
        @Size(max = 4000, message = "Реквизиты не длиннее 4000 символов") String instructions,
        FeeMode feeMode,
        @DecimalMin(value = "0", message = "Сумма не может быть отрицательной")
                @Digits(
                        integer = 10,
                        fraction = 2,
                        message = "Сумма должна содержать не более двух знаков после запятой")
                BigDecimal eventFeeAmount,
        @Pattern(regexp = "[A-Z]{3}", message = "Укажите трёхбуквенный код валюты") String currency,
        @Min(value = 0, message = "Срок не может быть отрицательным")
                @Max(value = 365, message = "Срок не более 365 дней")
                Integer dueDays) {
    public enum FeeMode {
        PER_DISCIPLINE,
        PER_EVENT
    }

    public EventPaymentSettings {
        feeMode = feeMode == null ? FeeMode.PER_DISCIPLINE : feeMode;
        currency = currency == null ? "RUB" : currency;
        dueDays = dueDays == null ? 3 : dueDays;
        instructions = instructions == null ? "" : instructions;
    }

    public static EventPaymentSettings fromJson(String value, ObjectMapper json) {
        try {
            var node = json.readTree(value == null ? "{}" : value).get("payment");
            // Existing events keep their discipline fees and the bodyless mark-paid contract.
            return node == null || node.isNull()
                    ? new EventPaymentSettings(true, "", null, null, "RUB", 3)
                    : json.readerFor(EventPaymentSettings.class)
                            .without(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(node);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid stored payment settings", e);
        }
    }
}
