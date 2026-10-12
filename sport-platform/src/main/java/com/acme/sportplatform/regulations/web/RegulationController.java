package com.acme.sportplatform.regulations.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.regulations.api.CreateRegulationTemplateRequest;
import com.acme.sportplatform.regulations.api.CreateRegulationVersionRequest;
import com.acme.sportplatform.regulations.api.RegulationTemplateResponse;
import com.acme.sportplatform.regulations.api.RegulationVersionResponse;
import com.acme.sportplatform.regulations.application.RegulationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class RegulationController {

    private final RegulationService regulationService;

    public RegulationController(RegulationService regulationService) {
        this.regulationService = regulationService;
    }

    @GetMapping("/regulation-templates")
    public ResponseEntity<List<RegulationTemplateResponse>> getTemplates() {
        return ResponseEntity.ok(regulationService.getTemplates());
    }

    @PostMapping("/regulation-templates")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public ResponseEntity<RegulationTemplateResponse> createTemplate(
            @RequestBody @Valid CreateRegulationTemplateRequest request
    ) {
        return ResponseEntity.ok(regulationService.createTemplate(request));
    }

    @GetMapping("/regulation-templates/{id}")
    public ResponseEntity<RegulationTemplateResponse> getTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(regulationService.getTemplate(id));
    }

    @PostMapping("/regulation-templates/{id}/versions")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public ResponseEntity<RegulationVersionResponse> createVersion(
            @PathVariable UUID id,
            @RequestBody @Valid CreateRegulationVersionRequest request
    ) {
        return ResponseEntity.ok(regulationService.createVersion(id, request));
    }

    @GetMapping("/regulation-versions")
    public ResponseEntity<List<RegulationVersionResponse>> getVersions() {
        return ResponseEntity.ok(regulationService.getVersions());
    }

    @GetMapping("/regulation-versions/{id}")
    public ResponseEntity<RegulationVersionResponse> getVersion(@PathVariable UUID id) {
        return ResponseEntity.ok(regulationService.getVersion(id));
    }

    @PostMapping("/regulation-versions/{id}/publish")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<RegulationVersionResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(regulationService.publishVersion(id));
    }
}