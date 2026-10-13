package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "Укажите refresh-токен")
        @Size(max = 256, message = "Refresh-токен слишком длинный") String refreshToken
) { }
