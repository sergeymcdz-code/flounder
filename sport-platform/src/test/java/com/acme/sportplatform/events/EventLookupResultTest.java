package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventLookupResultTest {
    @Test
    void preservesSchedulingMetadataWhenUsingTheConstructorWithoutSlug() {
        UUID eventId = UUID.randomUUID();
        UUID sportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        var openAt = OffsetDateTime.parse("2026-10-13T09:00:00+03:00");
        var closeAt = openAt.plusDays(1);
        var sessions = List.of(new EventSessionView(UUID.randomUUID(), eventId, "Утро", 1, closeAt, 60));
        String settings = "{\"lanes\":8}";

        var result = new EventLookupResult(eventId, sportId, "DRAFT", openAt, closeAt, sessions,
                venueId, ownerId, settings);

        assertEquals(eventId, result.id());
        assertEquals(sportId, result.sportId());
        assertEquals("DRAFT", result.status());
        assertEquals(openAt, result.registrationOpenAt());
        assertEquals(closeAt, result.registrationCloseAt());
        assertEquals(sessions, result.sessions());
        assertEquals(venueId, result.venueId());
        assertEquals(ownerId, result.createdByUserId());
        assertEquals(settings, result.settingsJson());
        assertNull(result.publicSlug());
    }

    @Test
    void preservesTheSixArgumentConstructorDefaults() {
        UUID eventId = UUID.randomUUID();
        var result = new EventLookupResult(eventId, null, "DRAFT", null, null, List.of());

        assertEquals(eventId, result.id());
        assertTrue(result.sessions().isEmpty());
        assertNull(result.venueId());
        assertNull(result.createdByUserId());
        assertEquals("{}", result.settingsJson());
        assertNull(result.publicSlug());
    }
}
