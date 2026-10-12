package com.acme.sportplatform.organizations.api;

import java.util.UUID;

public record VenueResponse(
        UUID id,
        String name,
        String countryCode,
        String city,
        String address,
        String timezone
) {
}