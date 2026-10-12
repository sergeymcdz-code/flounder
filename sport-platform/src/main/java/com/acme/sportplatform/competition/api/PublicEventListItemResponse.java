package com.acme.sportplatform.competition.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicEventListItemResponse(
        UUID id,
        UUID sportId,
        String title,
        String description,
        OffsetDateTime registrationOpenAt,
        OffsetDateTime registrationCloseAt,
        OffsetDateTime eventStartAt,
        OffsetDateTime eventEndAt,
        String status,
        String publicSlug
) {
}