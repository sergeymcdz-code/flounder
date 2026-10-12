package com.acme.sportplatform.organizations.api;

import jakarta.validation.constraints.NotBlank;

public record CreateVenueRequest(
        @NotBlank String name,
        String countryCode,
        String city,
        String address,
        @NotBlank String timezone
) {
}