package com.acme.sportplatform.organizations.api;

import jakarta.validation.constraints.*;

public record PublicOrganizationProfile(
        @NotNull @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,127}$", message = "Адрес: 3–128 строчных латинских букв, цифр и дефисов") String publicSlug,
        @Size(max = 2000, message = "Описание не должно превышать 2000 знаков") String description,
        @Size(max = 255) @Pattern(regexp = "^(https?://[^\\s]+)?$", message = "Укажите адрес сайта с https:// или http://") String website,
        @Size(max = 255) @Email(message = "Укажите корректный e-mail") String publicEmail,
        @Size(max = 32) @Pattern(regexp = "^[+0-9() .-]*$", message = "Укажите корректный телефон") String publicPhone,
        @NotNull Boolean isPublic, Boolean hasLogo) {}
