package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventPublicLookupResult(
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
        java.util.List<EventSessionView> sessions, UUID organizationId, UUID venueId, String city) {}
