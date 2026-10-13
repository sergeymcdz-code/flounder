package com.acme.sportplatform.events;

import java.util.UUID;

public record EventLookupResult(
        UUID id,
        UUID sportId,
        String status,
        java.time.OffsetDateTime registrationOpenAt,
        java.time.OffsetDateTime registrationCloseAt,
        java.util.List<EventSessionView> sessions,
        UUID venueId, UUID createdByUserId, String settingsJson) {
    public EventLookupResult(UUID id, UUID sportId, String status,
            java.time.OffsetDateTime registrationOpenAt, java.time.OffsetDateTime registrationCloseAt,
            java.util.List<EventSessionView> sessions) {
        this(id,sportId,status,registrationOpenAt,registrationCloseAt,sessions,null,null,"{}");
    }
}
