package com.acme.sportplatform.regulations.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRegulationTemplateRequest(
        @NotNull UUID sportId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        String description
) {
}