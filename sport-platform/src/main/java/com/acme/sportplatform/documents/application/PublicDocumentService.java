package com.acme.sportplatform.documents.application;

import com.acme.sportplatform.documents.api.PdfDocument;
import com.acme.sportplatform.documents.application.PrintFormService.Scope;
import com.acme.sportplatform.events.EventPublicLookup;
import com.github.benmanes.caffeine.cache.Cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class PublicDocumentService {
    private final PrintFormService forms;
    private final EventPublicLookup events;
    private final DocumentService documents;
    private final Cache<String, PdfDocument> cache;

    @Autowired
    public PublicDocumentService(
            EventPublicLookup events,
            DocumentService documents,
            @Qualifier("publicPdfCache") Cache<String, PdfDocument> cache,
            PrintFormService forms) {
        this.forms = forms;
        this.events = events;
        this.documents = documents;
        this.cache = cache;
    }

    public PublicDocumentService(
            EventPublicLookup events, DocumentService documents, Cache<String, PdfDocument> cache) {
        this(events, documents, cache, null);
    }

    public PdfDocument awards(String slug) {
        var event = events.getPublicBySlug(slug);
        return cache.get(
                slug + ":" + event.id() + ":awards",
                key -> forms.awards(event.id(), true, Scope.AGE_GROUP, 3, null));
    }

    public PdfDocument get(String slug, boolean results) {
        // Recheck event visibility even on a cache hit; never cache organizer drafts.
        var event = events.getPublicBySlug(slug);
        return cache.get(
                slug + ":" + event.id() + ":" + results,
                key -> documents.generate(event.id(), results, true));
    }
}
