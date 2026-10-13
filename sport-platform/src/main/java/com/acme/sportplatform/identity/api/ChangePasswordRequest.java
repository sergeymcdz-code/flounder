package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Укажите текущий пароль") String currentPassword,
        String newPassword
) { }
