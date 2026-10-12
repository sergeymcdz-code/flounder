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

        @NotNull
        UUID disciplineTemplateId,

        @NotBlank
        String code,

        @NotBlank
        String name,

        @Positive
        Integer participantLimit,

        @DecimalMin(value = "0.00")
        BigDecimal entryFeeAmount,

        @Pattern(regexp = "^[A-Z]{3}$")
        String entryFeeCurrency,

        JsonNode settingsJson
) {
}