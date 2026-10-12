package com.acme.sportplatform.competition.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PublicEventDetailsResponse(
        UUID id,
        UUID sportId,
        String title,
        String description,
        OffsetDateTime registrationOpenAt,
        OffsetDateTime registrationCloseAt,
        OffsetDateTime eventStartAt,
        OffsetDateTime eventEndAt,
        String status,
        String publicSlug,
        List<PublicEventDisciplineResponse> disciplines
) {
}