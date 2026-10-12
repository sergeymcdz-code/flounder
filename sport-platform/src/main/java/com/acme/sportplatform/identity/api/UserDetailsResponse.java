package com.acme.sportplatform.identity.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserDetailsResponse(
        UUID id,
        String email,
        String phone,
        String status,
        OffsetDateTime createdAt,
        Profile profile,
        List<String> roles
) {
        public record Profile(
                String firstName,
                String lastName,
                String middleName,
                LocalDate birthDate,
                String gender,
                String city,
                String countryCode,
                String clubName
        ) {}
}