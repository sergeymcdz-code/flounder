package com.acme.sportplatform.documents.application;

import com.acme.sportplatform.documents.api.PdfDocument;
import com.acme.sportplatform.documents.infrastructure.PdfRenderer;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventPublicLookupResult;
import com.acme.sportplatform.standings.StandingsLookup;
import com.acme.sportplatform.standings.StandingsView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {
    private final DocumentDataService data;
    private final PdfRenderer renderer;
    private final EventLookup events;
    private final StandingsLookup standings;

    public DocumentService(DocumentDataService data, PdfRenderer renderer, EventLookup events,
            StandingsLookup standings) {
        this.data = data;
        this.renderer = renderer;
        this.events = events;
        this.standings = standings;
    }

    public PdfDocument standings(UUID eventId) {
        return standingsPdf(eventId, standings.forEvent(eventId, false));
    }

    public PdfDocument publicStandings(EventPublicLookupResult event) {
        return standingsPdf(event.id(), standings.forPublicEvent(event));
    }

    private PdfDocument standingsPdf(UUID eventId, StandingsView view) {
        var header = events.protocolHeader(eventId);
        return new PdfDocument(renderer.renderStandings(data.header(eventId), view), "Зачёт-" +
                (header.publicSlug() == null ? eventId : header.publicSlug()) + ".pdf");
    }

    public PdfDocument generate(UUID eventId, boolean results, boolean publishedOnly) {
        var protocol = data.protocol(eventId, publishedOnly);
        var header = events.protocolHeader(eventId);
        String identifier = header.publicSlug() == null ? eventId.toString() : header.publicSlug();
        String date = LocalDate.parse(protocol.header().date(), DateTimeFormatter.ofPattern("dd.MM.yyyy")).toString();
        return new PdfDocument(renderer.render(protocol, results),
                (results ? "Итоговый-протокол-" : "Стартовый-протокол-") + identifier + "-" + date + ".pdf");
    }
}
