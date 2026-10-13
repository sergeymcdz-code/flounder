package com.acme.sportplatform.organizations.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record CreateVenueRequest(
        @NotBlank String name,
        String countryCode,
        String city,
        String address,
        @NotBlank String timezone,
        Map<String, Object> venueMeta
) {
    public CreateVenueRequest {
        venueMeta = venueMeta == null ? Map.of() : venueMeta;
    }
}