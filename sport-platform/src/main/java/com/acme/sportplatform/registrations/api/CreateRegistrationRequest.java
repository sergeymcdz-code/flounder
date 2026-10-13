package com.acme.sportplatform.registrations.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record CreateRegistrationRequest(
        @NotNull(message = "Укажите дисциплину") UUID eventDisciplineId,
        JsonNode registrationMeta,
        @jakarta.validation.constraints.Positive(
                        message = "Заявочное время должно быть положительным")
                Long seedTimeMs,
        String seedTime) {}
