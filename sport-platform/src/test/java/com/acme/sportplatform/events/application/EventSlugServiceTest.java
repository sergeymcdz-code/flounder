package com.acme.sportplatform.events.application;

import org.junit.jupiter.api.Test;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventSlugServiceTest {
    private final EventRepository events = mock(EventRepository.class);
    private final EventSlugService slugs = new EventSlugService(events);

    @Test
    void transliteratesRussianTitle() {
        assertEquals("kubok-samary-2026", slugs.generate("Кубок Самары 2026"));
    }

    @Test
    void appendsFourCharactersOnlyOnCollision() {
        when(events.existsByPublicSlug("kubok-samary-2026")).thenReturn(true);
        assertTrue(slugs.generate("Кубок Самары 2026").matches("kubok-samary-2026-[a-f0-9]{4}"));
    }

    @Test
    void boundsLengthAndHandlesEmptyTitles() {
        assertEquals("event", slugs.generate("!!!"));
        when(events.existsByPublicSlug("a".repeat(250))).thenReturn(true);
        assertEquals(255, slugs.generate("a".repeat(300)).length());
    }
}
