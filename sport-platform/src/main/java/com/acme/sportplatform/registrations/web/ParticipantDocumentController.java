package com.acme.sportplatform.registrations.web;

import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.files.FileLookup;
import com.acme.sportplatform.registrations.api.ParticipantDocumentResponse;
import com.acme.sportplatform.registrations.application.ParticipantDocumentService;
import com.acme.sportplatform.registrations.domain.DocumentType;

@RestController
@RequestMapping("/api/v1")
public class ParticipantDocumentController {
    private final ParticipantDocumentService service;
    public ParticipantDocumentController(ParticipantDocumentService service) { this.service = service; }
    @PostMapping(path = "/profiles/{profileId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PARTICIPANT','ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ResponseEntity<ParticipantDocumentResponse> upload(@PathVariable UUID profileId,
            @RequestParam DocumentType type, @RequestPart MultipartFile file,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return ResponseEntity.status(201).body(service.upload(profileId, eventId, type, file, issuedAt, validUntil, principal));
    }
    @GetMapping("/profiles/{profileId}/documents")
    @PreAuthorize("hasAnyRole('PARTICIPANT','ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public List<ParticipantDocumentResponse> list(@PathVariable UUID profileId, @RequestParam(required = false) UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) { return service.list(profileId, eventId, principal); }
    @PostMapping("/profiles/{profileId}/documents/{id}/review")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ParticipantDocumentResponse review(@PathVariable UUID profileId, @PathVariable UUID id,
            @RequestParam UUID eventId,
            @jakarta.validation.Valid @RequestBody com.acme.sportplatform.registrations.api.ReviewParticipantDocumentRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.review(profileId, id, eventId, request, principal);
    }
    @DeleteMapping("/profiles/{profileId}/documents/{id}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public ResponseEntity<Void> delete(@PathVariable UUID profileId, @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        service.delete(profileId, id, principal); return ResponseEntity.noContent().build();
    }
    @GetMapping("/documents/{id}/download")
    @PreAuthorize("hasAnyRole('PARTICIPANT','ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ResponseEntity<FileLookup.SignedDownload> download(@PathVariable UUID id, @RequestParam(required = false) UUID eventId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.download(id, eventId, principal)); }
    @GetMapping("/public/documents/{id}/content")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> content(@PathVariable UUID id, @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) Long expires, @RequestParam(required = false) String signature,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        var content = service.content(id, eventId, expires, signature, principal);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.document().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.document().originalName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(content.document().sizeBytes())
                .body(new org.springframework.core.io.InputStreamResource(content.input()));
    }
}
