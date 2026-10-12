package com.acme.sportplatform.sports.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSportRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name
) {
}