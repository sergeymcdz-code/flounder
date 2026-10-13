package com.acme.sportplatform.documents.application;

import com.acme.sportplatform.documents.api.PdfDocument;
import com.acme.sportplatform.documents.infrastructure.PdfRenderer;
import com.acme.sportplatform.events.EventLookup;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {
    private final DocumentDataService data;
    private final PdfRenderer renderer;
    private final EventLookup events;

    public DocumentService(DocumentDataService data, PdfRenderer renderer, EventLookup events) {
        this.data = data;
        this.renderer = renderer;
        this.events = events;
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
