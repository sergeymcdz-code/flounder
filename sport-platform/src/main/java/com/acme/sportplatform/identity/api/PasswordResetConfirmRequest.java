package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Укажите токен восстановления")
        @Size(max = 256, message = "Токен восстановления слишком длинный") String token,
        @NotBlank(message = "Укажите новый пароль") String newPassword
) { }
