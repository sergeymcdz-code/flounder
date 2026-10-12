package com.acme.sportplatform.events.api;

import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

public record EventSessionRequest(
        @Size(max = 255, message = "Название не длиннее 255 символов") String name,
        @Positive(message = "Номер должен быть положительным") Integer orderNo,
        OffsetDateTime startsAt,
        @PositiveOrZero(message = "Перерыв не может быть отрицательным")
                Integer breakBetweenEventsSec) {}
