package com.acme.sportplatform.documents.application;

import com.acme.sportplatform.documents.api.PdfDocument;
import com.acme.sportplatform.documents.infrastructure.PdfRenderer;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventPublicLookup;
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
    private final EventPublicLookup publicEvents;
    private final StandingsLookup standings;

    public DocumentService(DocumentDataService data, PdfRenderer renderer, EventLookup events,
            StandingsLookup standings, EventPublicLookup publicEvents) {
        this.data = data;
        this.renderer = renderer;
        this.events = events;
        this.standings = standings;
        this.publicEvents = publicEvents;
    }

    public PdfDocument standings(UUID eventId) {
        return standingsPdf(eventId, standings.forEvent(eventId, false));
    }

    public PdfDocument publicStandings(String slug) {
        var view = standings.publicBySlug(slug);
        return standingsPdf(publicEvents.getPublicBySlug(slug).id(), view);
    }

    private PdfDocument standingsPdf(UUID eventId, StandingsView view) {
        var header = events.protocolHeader(eventId);
        return new PdfDocument(renderer.renderStandings(header, view), "Зачёт-" +
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
