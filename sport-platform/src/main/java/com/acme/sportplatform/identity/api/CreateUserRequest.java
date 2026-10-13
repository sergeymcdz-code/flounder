package com.acme.sportplatform.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        String clubName,
        java.util.List<String> roles
) {
    public CreateUserRequest(String email, String password, String phone, String firstName, String lastName, String middleName,
                             LocalDate birthDate, String gender, String city, String countryCode, String clubName) {
        this(email, password, phone, firstName, lastName, middleName, birthDate, gender, city, countryCode, clubName, null);
    }
}
