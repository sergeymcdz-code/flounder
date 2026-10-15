package com.acme.sportplatform.documents;

import com.acme.sportplatform.documents.application.DocumentDataService;
import com.acme.sportplatform.documents.application.DocumentService;
import com.acme.sportplatform.documents.domain.Protocol;
import com.acme.sportplatform.documents.infrastructure.PdfRenderer;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventProtocolHeader;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "samara-cup")
    void filenamesUsePublicSlugOrIdAndEventDateInVenueTimezone(String slug) {
        var data = mock(DocumentDataService.class);
        var renderer = mock(PdfRenderer.class);
        var events = mock(EventLookup.class);
        var service = new DocumentService(data, renderer, events, mock(com.acme.sportplatform.standings.StandingsLookup.class),
                mock(com.acme.sportplatform.events.EventPublicLookup.class));
        var id = UUID.randomUUID();
        var start = OffsetDateTime.parse("2026-10-16T21:00:00Z");
        var header = new EventProtocolHeader(id, "Кубок", UUID.randomUUID(), UUID.randomUUID(),
                start, start.plusHours(6), slug);
        when(events.protocolHeader(id)).thenReturn(header);
        var protocol = new Protocol(new Protocol.Header("Кубок", "Бассейн", "17.10.2026", "Федерация", "Время"), List.of());
        when(data.protocol(id, true)).thenReturn(protocol);
        when(renderer.render(eq(protocol), anyBoolean())).thenReturn(new byte[] {1});
        var identifier = slug == null ? id.toString() : slug;
        assertThat(service.generate(id, false, true).filename()).isEqualTo("Стартовый-протокол-" + identifier + "-2026-10-17.pdf");
        assertThat(service.generate(id, true, true).filename()).isEqualTo("Итоговый-протокол-" + identifier + "-2026-10-17.pdf");
    }
}
