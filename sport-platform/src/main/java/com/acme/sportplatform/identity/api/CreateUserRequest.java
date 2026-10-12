package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateUserRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        String phone,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String middleName,
        @NotNull LocalDate birthDate,
        String gender,
        String city,
        String countryCode,
        String clubName
) {
}