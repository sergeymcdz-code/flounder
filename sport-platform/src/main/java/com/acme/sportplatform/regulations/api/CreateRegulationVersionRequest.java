package com.acme.sportplatform.regulations.api;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRegulationVersionRequest(
        @Positive int versionNo,
        @NotNull @Valid RegulationRules rulesJson,
        String notes,
        LocalDate effectiveFrom
) {
}