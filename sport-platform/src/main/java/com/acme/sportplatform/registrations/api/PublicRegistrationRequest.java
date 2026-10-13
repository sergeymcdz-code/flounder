package com.acme.sportplatform.registrations.api;

import com.acme.sportplatform.identity.ProfileData;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.*;

public record PublicRegistrationRequest(
        @Valid @NotNull(message = "Укажите профиль") ProfileData profile,
        @NotEmpty(message = "Выберите дисциплины")
        @Size(max = 3, message = "Выберите не больше трёх видов программы") List<@Valid Item> items) {
    public record Item(
            @NotNull(message = "Укажите дисциплину") UUID eventDisciplineId,
            @NotNull(message = "Укажите заявочное время")
                    @Positive(message = "Заявочное время должно быть положительным")
                    Long seedTimeMs) {}
}
