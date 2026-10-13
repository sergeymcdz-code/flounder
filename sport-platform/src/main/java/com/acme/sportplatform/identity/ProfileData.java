package com.acme.sportplatform.identity;

import java.time.LocalDate;

public record ProfileData(
        @jakarta.validation.constraints.NotBlank(message = "Укажите фамилию")
                @jakarta.validation.constraints.Size(
                        max = 128,
                        message = "Фамилия не длиннее 128 символов")
                String lastName,
        @jakarta.validation.constraints.NotBlank(message = "Укажите имя")
                @jakarta.validation.constraints.Size(
                        max = 128,
                        message = "Имя не длиннее 128 символов")
                String firstName,
        @jakarta.validation.constraints.Size(
                        max = 128,
                        message = "Отчество не длиннее 128 символов")
                String middleName,
        @jakarta.validation.constraints.NotNull(message = "Укажите дату рождения")
                @jakarta.validation.constraints.PastOrPresent(
                        message = "Дата рождения не может быть в будущем")
                LocalDate birthDate,
        @jakarta.validation.constraints.NotNull(message = "Укажите пол")
                @jakarta.validation.constraints.Pattern(
                        regexp = "M|F|X",
                        message = "Пол должен быть M, F или X")
                String gender,
        @jakarta.validation.constraints.Size(max = 255, message = "Клуб не длиннее 255 символов")
                String club) {}
