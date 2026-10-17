package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {
    public LoginRequest {
        email = com.acme.sportplatform.identity.EmailAddress.normalize(email);
    }

}