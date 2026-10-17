package com.acme.sportplatform.documents;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.documents.api.PdfDocument;
import com.acme.sportplatform.documents.application.*;
import com.acme.sportplatform.events.*;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicDocumentServiceTest {
    @Test
    void cachesBySlugAndProtocolForExactlySixtySecondsAndRechecksVisibility() {
        var events = mock(EventPublicLookup.class);
        var documents = mock(DocumentService.class);
        var ticks = new AtomicLong();
        var cache = Caffeine.newBuilder().ticker(ticks::get).expireAfterWrite(Duration.ofSeconds(60))
                .<String, PdfDocument>build();
        var service = new PublicDocumentService(events, documents, cache);
        var eventId = UUID.randomUUID();
        var otherId = UUID.randomUUID();
        when(events.getPublicBySlug("cup")).thenReturn(event(eventId, "cup"));
        when(events.getPublicBySlug("other")).thenReturn(event(otherId, "other"));
        var start = new PdfDocument(new byte[] {1}, "start.pdf");
        var results = new PdfDocument(new byte[] {2}, "results.pdf");
        when(documents.generate(any(), anyBoolean(), eq(true))).thenReturn(start);
        when(documents.generate(eventId, true, true)).thenReturn(results);
        assertThat(service.get("cup", false)).isSameAs(start);
        ticks.set(Duration.ofSeconds(59).toNanos());
        assertThat(service.get("cup", false)).isSameAs(start);
        verify(documents).generate(eventId, false, true);
        assertThat(service.get("cup", true)).isSameAs(results);
        service.get("other", false);
        verify(documents).generate(otherId, false, true);
        ticks.set(Duration.ofSeconds(60).toNanos());
        service.get("cup", false);
        verify(documents, times(2)).generate(eventId, false, true);
        when(events.getPublicBySlug("cup")).thenThrow(new BusinessException("events.public_not_found", "Публичное событие не найдено"));
        assertThatThrownBy(() -> service.get("cup", false)).isInstanceOf(BusinessException.class);
        verify(documents, times(2)).generate(eventId, false, true);
    }

    @Test
    void failedGenerationIsNotCached() {
        var events = mock(EventPublicLookup.class);
        var documents = mock(DocumentService.class);
        var service = new PublicDocumentService(events, documents, Caffeine.newBuilder().build());
        var id = UUID.randomUUID();
        when(events.getPublicBySlug("cup")).thenReturn(event(id, "cup"));
        when(documents.generate(id, false, true)).thenThrow(new BusinessException("documents.generation_failed", "Ошибка"))
                .thenReturn(new PdfDocument(new byte[] {1}, "start.pdf"));
        assertThatThrownBy(() -> service.get("cup", false)).isInstanceOf(BusinessException.class);
        assertThat(service.get("cup", false).content()).containsExactly((byte)1);
        verify(documents, times(2)).generate(id, false, true);
    }

    private EventPublicLookupResult event(UUID id, String slug) {
        return new EventPublicLookupResult(id, UUID.randomUUID(), "Кубок", null, null, null, null,
                null, "PUBLISHED", slug, List.of(), null, null, null);
    }
}
