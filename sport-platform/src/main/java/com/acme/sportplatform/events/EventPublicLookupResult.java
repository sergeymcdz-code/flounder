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
        java.util.List<EventSessionView> sessions,
        UUID organizationId, UUID venueId, String city, EventPaymentSettings payment) {
    public EventPublicLookupResult(UUID id, UUID sportId, String title, String description,
            OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt, OffsetDateTime eventStartAt,
            OffsetDateTime eventEndAt, String status, String publicSlug, java.util.List<EventSessionView> sessions) {
        this(id,sportId,title,description,registrationOpenAt,registrationCloseAt,eventStartAt,eventEndAt,status,publicSlug,sessions,null,null,null,null);
    }
    public EventPublicLookupResult(UUID id, UUID sportId, String title, String description,
            OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt, OffsetDateTime eventStartAt,
            OffsetDateTime eventEndAt, String status, String publicSlug, java.util.List<EventSessionView> sessions,
            UUID organizationId, UUID venueId, String city) {
        this(id, sportId, title, description, registrationOpenAt, registrationCloseAt, eventStartAt,
                eventEndAt, status, publicSlug, sessions, organizationId, venueId, city, null);
    }
}
