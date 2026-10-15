package com.acme.sportplatform.documents.web;

import com.acme.sportplatform.documents.api.PdfDocument;
import com.acme.sportplatform.documents.application.DocumentService;
import com.acme.sportplatform.documents.application.PublicDocumentService;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DocumentsController {
    private final EventAccessService access;
    private final DocumentService documents;
    private final PublicDocumentService published;

    public DocumentsController(EventAccessService access, DocumentService documents, PublicDocumentService published) {
        this.access = access;
        this.documents = documents;
        this.published = published;
    }

    @GetMapping("/events/{eventId}/documents/start-list.pdf")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ResponseEntity<byte[]> startList(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return response(documents.generate(eventId, false, false));
    }

    @GetMapping("/events/{eventId}/documents/results.pdf")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ResponseEntity<byte[]> results(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return response(documents.generate(eventId, true, false));
    }

    @GetMapping("/public/events/{slug}/start-list.pdf")
    public ResponseEntity<byte[]> publicStartList(@PathVariable String slug) {
        return response(published.get(slug, false));
    }

    @GetMapping("/public/events/{slug}/results.pdf")
    public ResponseEntity<byte[]> publicResults(@PathVariable String slug) {
        return response(published.get(slug, true));
    }

    @GetMapping("/events/{eventId}/documents/standings.pdf")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ResponseEntity<byte[]> standings(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId, principal);
        return response(documents.standings(eventId));
    }

    @GetMapping("/public/events/{slug}/standings.pdf")
    public ResponseEntity<byte[]> publicStandings(@PathVariable String slug) {
        return response(documents.publicStandings(slug));
    }

    private ResponseEntity<byte[]> response(PdfDocument pdf) {
        // Server caches public generation for 60s; clients must recheck visibility on each download.
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.filename(), StandardCharsets.UTF_8).build().toString())
                .contentLength(pdf.content().length).body(pdf.content());
    }
}
