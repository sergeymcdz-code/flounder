package com.acme.sportplatform.organizations.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record CreateVenueRequest(
        @NotBlank(message = "Укажите название площадки") String name,
        String countryCode,
        String city,
        String address,
        @NotBlank(message = "Укажите часовой пояс") String timezone,
        Map<String, Object> venueMeta
) {
    public CreateVenueRequest {
        venueMeta = venueMeta == null ? Map.of() : venueMeta;
    }
}