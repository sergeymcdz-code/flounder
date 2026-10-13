package com.acme.sportplatform.results.api;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record SwimResultRequest(
        @NotNull(message = "Укажите участника заплыва") UUID competitionUnitEntryId,
        String time,
        @Positive(message = "Время должно быть положительным") Long timeMs,
        @NotNull(message = "Укажите статус результата")
                @Pattern(
                        regexp = "VALID|DNS|DNF|DSQ",
                        message = "Статус должен быть VALID, DNS, DNF или DSQ")
                String status) {}
