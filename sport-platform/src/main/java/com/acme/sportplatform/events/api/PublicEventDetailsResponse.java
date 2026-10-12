package com.acme.sportplatform.events.api;

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
        List<DisciplineView> disciplines
) {
    public record DisciplineView(
            UUID id,
            String code,
            String name,
            String competitionFormat,
            String unitType,
            String resultType,
            String rankingStrategy,
            Integer participantLimit,
            Integer entryFeeAmount,
            String entryFeeCurrency,
            String settingsJson
    ) {
    }
}