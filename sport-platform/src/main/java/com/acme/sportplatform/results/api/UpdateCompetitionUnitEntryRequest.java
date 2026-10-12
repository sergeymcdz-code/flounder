package com.acme.sportplatform.results.api;

import jakarta.validation.constraints.*;

public record UpdateCompetitionUnitEntryRequest(
        @NotNull(message = "Укажите дорожку")
                @Min(value = 1, message = "Номер дорожки должен быть положительным")
                Integer laneOrPosition) {}
