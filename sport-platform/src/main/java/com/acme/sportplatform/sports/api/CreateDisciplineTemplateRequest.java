package com.acme.sportplatform.sports.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDisciplineTemplateRequest(
        @NotNull UUID sportId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 64) String competitionFormat,
        @NotBlank @Size(max = 64) String unitType,
        @NotBlank @Size(max = 64) String resultType,
        @NotBlank @Size(max = 64) String rankingStrategy
) {
}