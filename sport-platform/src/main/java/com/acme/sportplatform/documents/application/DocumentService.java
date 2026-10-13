package com.acme.sportplatform.documents.application;

import com.acme.sportplatform.documents.api.PdfDocument;
import com.acme.sportplatform.documents.infrastructure.PdfRenderer;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {
    private final DocumentDataService data;
    private final PdfRenderer renderer;

    public DocumentService(DocumentDataService data, PdfRenderer renderer) {
        this.data = data;
        this.renderer = renderer;
    }

    public PdfDocument generate(UUID eventId, boolean results, boolean publishedOnly) {
        var protocol = data.protocol(eventId, publishedOnly);
        return new PdfDocument(renderer.render(protocol, results),
                (results ? "Итоговый-протокол-" : "Стартовый-протокол-") + eventId + ".pdf");
    }
}
