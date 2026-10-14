package com.acme.sportplatform.competition.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateEventDisciplineRequest(
        @NotNull(groups = Create.class, message = "Укажите шаблон дисциплины")
                UUID disciplineTemplateId,
        @jakarta.validation.constraints.Size(max = 64, message = "Код не длиннее 64 символов")
                String code,
        @jakarta.validation.constraints.Size(
                        max = 255,
                        message = "Название не длиннее 255 символов")
                String name,
        @Positive(message = "Лимит участников должен быть положительным") Integer participantLimit,
        @DecimalMin(value = "0.00", message = "Взнос не может быть отрицательным")
                BigDecimal entryFeeAmount,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Валюта должна состоять из трёх заглавных букв")
                String entryFeeCurrency,
        JsonNode settingsJson,
        Integer distanceM,
        com.acme.sportplatform.competition.Stroke stroke,
        String gender,
        java.util.List<com.acme.sportplatform.competition.AgeGroup> ageGroups,
        com.acme.sportplatform.competition.SeedingMode seedingMode,
        UUID sessionId,
        Integer orderInSession,
        Integer heatDurationSec,
        @Positive(message = "Лимит должен быть положительным") Integer maxParticipants,
        java.util.Map<String, @Positive(message = "Лимит группы должен быть положительным") Integer> limitsByAgeGroup) {
    public CreateEventDisciplineRequest(
        @NotNull(groups = Create.class, message = "Укажите шаблон дисциплины")
                UUID disciplineTemplateId,
        @jakarta.validation.constraints.Size(max = 64, message = "Код не длиннее 64 символов")
                String code,
        @jakarta.validation.constraints.Size(
                        max = 255,
                        message = "Название не длиннее 255 символов")
                String name,
        @Positive(message = "Лимит участников должен быть положительным") Integer participantLimit,
        @DecimalMin(value = "0.00", message = "Взнос не может быть отрицательным")
                BigDecimal entryFeeAmount,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Валюта должна состоять из трёх заглавных букв")
                String entryFeeCurrency,
        JsonNode settingsJson,
        Integer distanceM,
        com.acme.sportplatform.competition.Stroke stroke,
        String gender,
        java.util.List<com.acme.sportplatform.competition.AgeGroup> ageGroups,
        com.acme.sportplatform.competition.SeedingMode seedingMode,
        UUID sessionId,
        Integer orderInSession,
        Integer heatDurationSec) {
        this(disciplineTemplateId, code, name, participantLimit, entryFeeAmount, entryFeeCurrency, settingsJson, distanceM, stroke, gender, ageGroups, seedingMode, sessionId, orderInSession, heatDurationSec, null, null);
    }

    public interface Create {}
}