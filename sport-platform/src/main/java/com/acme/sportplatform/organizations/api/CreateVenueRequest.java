package com.acme.sportplatform.organizations.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record CreateVenueRequest(
        @NotBlank(message = "Укажите название площадки") String name,
        String countryCode,
        String city,
        String address,
        @NotBlank(message = "Укажите часовой пояс") String timezone,
        Map<String, Object> venueMeta,
        java.util.UUID organizationId
) {
    public CreateVenueRequest(String name, String countryCode, String city, String address, String timezone, Map<String, Object> venueMeta) {
        this(name, countryCode, city, address, timezone, venueMeta, null);
    }
    public CreateVenueRequest {
        venueMeta = venueMeta == null ? Map.of() : venueMeta;
    }
}