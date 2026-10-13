package com.acme.sportplatform.organizations.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddOrganizationMemberRequest(
        @NotNull(message = "Укажите пользователя") UUID userId,
        @NotBlank(message = "Укажите роль участника") String memberRole
) {
}