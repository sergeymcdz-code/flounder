package com.acme.sportplatform.documents.web;

import com.acme.sportplatform.documents.application.*;
import com.acme.sportplatform.documents.application.PrintFormService.*;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class PrintFormsController {
    private final EventAccessService access;
    private final PrintFormService forms;
    private final PublicDocumentService published;
    public PrintFormsController(EventAccessService access,PrintFormService forms,PublicDocumentService published) { this.access=access; this.forms=forms; this.published=published; }
    @GetMapping("/events/{eventId}/documents/heat-cards.pdf")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ResponseEntity<byte[]> cards(@PathVariable UUID eventId,@RequestParam(required=false) UUID disciplineId,@RequestParam(required=false) UUID sessionId,
            @RequestParam(defaultValue="A5") CardFormat format,@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId,principal); return DocumentsController.response(forms.cards(eventId,disciplineId,sessionId,format));
    }
    @GetMapping("/events/{eventId}/documents/awards.pdf")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ResponseEntity<byte[]> awards(@PathVariable UUID eventId,@RequestParam(defaultValue="AGE_GROUP") Scope scope,@RequestParam(defaultValue="3") int places,
            @RequestParam(required=false) UUID disciplineId,@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId,principal); return DocumentsController.response(forms.awards(eventId,false,scope,places,disciplineId));
    }
    @GetMapping("/events/{eventId}/documents/diplomas.pdf")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public ResponseEntity<byte[]> diplomas(@PathVariable UUID eventId,@RequestParam(defaultValue="AGE_GROUP") Scope scope,@RequestParam(defaultValue="3") int places,
            @RequestParam(required=false) UUID disciplineId,@RequestParam(required=false) String ageGroupCode,@RequestParam(defaultValue="false") boolean sample,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanManage(eventId,principal); return DocumentsController.response(forms.diplomas(eventId,scope,places,disciplineId,ageGroupCode,sample,principal.getUserId()));
    }
    @PostMapping("/events/{eventId}/documents/diploma-background")
    @PreAuthorize("hasAnyRole('ORGANIZER','OPERATOR','PLATFORM_ADMIN')")
    public Map<String,String> upload(@PathVariable UUID eventId,@RequestParam(value="file",required=false) MultipartFile file,@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        access.assertCanModify(eventId,principal);
        if(file==null) throw new com.acme.sportplatform.common.exception.BusinessException("documents.invalid_background","Выберите файл фона");
        return forms.uploadBackground(eventId,file,principal.getUserId());
    }
    @GetMapping("/public/events/{slug}/awards.pdf")
    public ResponseEntity<byte[]> publicAwards(@PathVariable String slug,@RequestParam(defaultValue="AGE_GROUP") Scope scope,
            @RequestParam(defaultValue="3") int places,@RequestParam(required=false) UUID disciplineId) {
        return DocumentsController.response(published.awards(slug,scope,places,disciplineId));
    }
}
