package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @Email(message = "Некорректный email") @NotBlank(message = "Укажите email") String email
) { }
