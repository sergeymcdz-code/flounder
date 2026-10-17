package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateUserRequest(
        @Email @NotBlank @Size(max = 255, message = "Не более {max} символов") String email,
        @NotBlank @Size(min = 8, max = 72, message = "Пароль должен содержать от 8 до 72 символов") String password,
        @Size(max = 32, message = "Не более {max} символов") String phone,
        @NotBlank @Size(max = 128, message = "Не более {max} символов") String firstName,
        @NotBlank @Size(max = 128, message = "Не более {max} символов") String lastName,
        @Size(max = 128, message = "Не более {max} символов") String middleName,
        @NotNull LocalDate birthDate,
        @Size(max = 16, message = "Не более {max} символов") String gender,
        @Size(max = 128, message = "Не более {max} символов") String city,
        @Size(max = 8, message = "Не более {max} символов") String countryCode,
        @Size(max = 255, message = "Не более {max} символов") String clubName,
        List<String> roles
) {
    public CreateUserRequest {
        email = com.acme.sportplatform.identity.EmailAddress.normalize(email);
    }

    public CreateUserRequest(String email, String password, String phone, String firstName, String lastName, String middleName,
                             LocalDate birthDate, String gender, String city, String countryCode, String clubName) {
        this(email, password, phone, firstName, lastName, middleName, birthDate, gender, city, countryCode, clubName, null);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "Пароль слишком длинный")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
