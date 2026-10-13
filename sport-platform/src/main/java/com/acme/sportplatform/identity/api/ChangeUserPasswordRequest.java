package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUserPasswordRequest(
        @NotBlank(message = "Укажите пароль") @Size(min = 8, max = 72, message = "Пароль должен содержать от 8 до 72 символов") String newPassword) {}
