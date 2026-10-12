package com.acme.sportplatform.identity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record ProfileLookupResult(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String middleName,
        LocalDate birthDate,
        String gender,
        String city,
        String countryCode,
        String clubName,
        Map<String, Object> sportMeta
) {
}