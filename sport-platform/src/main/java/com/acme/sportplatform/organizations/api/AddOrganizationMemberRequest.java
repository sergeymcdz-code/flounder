package com.acme.sportplatform.organizations.api;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddOrganizationMemberRequest(UUID userId,
        @NotBlank(message = "Укажите роль участника") String memberRole,
        @Email(message = "Укажите корректный email") String email) {
    public AddOrganizationMemberRequest(UUID userId, String memberRole) { this(userId, memberRole, null); }
}
