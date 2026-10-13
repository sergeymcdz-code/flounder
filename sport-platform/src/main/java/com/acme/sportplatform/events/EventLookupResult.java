package com.acme.sportplatform.events;

import java.util.UUID;

public record EventLookupResult(
        UUID id,
        UUID sportId,
        String status,
        java.time.OffsetDateTime registrationOpenAt,
        java.time.OffsetDateTime registrationCloseAt,
        java.util.List<EventSessionView> sessions,
        UUID venueId,
        UUID createdByUserId,
        String settingsJson,
        String publicSlug,
        Integer maxParticipants) {
    public EventLookupResult(
        UUID id,
        UUID sportId,
        String status,
        java.time.OffsetDateTime registrationOpenAt,
        java.time.OffsetDateTime registrationCloseAt,
        java.util.List<EventSessionView> sessions,
        UUID venueId,
        UUID createdByUserId,
        String settingsJson,
        String publicSlug) {
        this(id, sportId, status, registrationOpenAt, registrationCloseAt, sessions, venueId, createdByUserId, settingsJson, publicSlug, null);
    }

    public boolean isPublic() {
        return status != null && java.util.Set.of(
                "PUBLISHED", "REGISTRATION_OPEN", "REGISTRATION_CLOSED", "LIVE", "COMPLETED", "ARCHIVED")
                .contains(status);
    }

    public EventLookupResult(UUID id, UUID sportId, String status,
            java.time.OffsetDateTime registrationOpenAt, java.time.OffsetDateTime registrationCloseAt,
            java.util.List<EventSessionView> sessions, UUID venueId, UUID createdByUserId, String settingsJson) {
        this(id, sportId, status, registrationOpenAt, registrationCloseAt, sessions,
                venueId, createdByUserId, settingsJson, null);
    }

    public EventLookupResult(UUID id, UUID sportId, String status,
            java.time.OffsetDateTime registrationOpenAt, java.time.OffsetDateTime registrationCloseAt,
            java.util.List<EventSessionView> sessions) {
        this(id, sportId, status, registrationOpenAt, registrationCloseAt, sessions, null, null, "{}");
    }
}
