package com.acme.sportplatform.sports.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.sports.api.CreateDisciplineTemplateRequest;
import com.acme.sportplatform.sports.api.CreateSportRequest;
import com.acme.sportplatform.sports.api.DisciplineTemplateResponse;
import com.acme.sportplatform.sports.api.SportResponse;
import com.acme.sportplatform.sports.application.SportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class SportsController {

    private final SportService sportService;

    public SportsController(SportService sportService) {
        this.sportService = sportService;
    }

    @GetMapping("/sports")
    public ResponseEntity<List<SportResponse>> getSports() {
        return ResponseEntity.ok(sportService.getSports());
    }

    @PostMapping("/sports")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<SportResponse> createSport(@RequestBody @Valid CreateSportRequest request) {
        return ResponseEntity.ok(sportService.createSport(request));
    }

    @GetMapping("/discipline-templates")
    public ResponseEntity<List<DisciplineTemplateResponse>> getDisciplineTemplates() {
        return ResponseEntity.ok(sportService.getDisciplineTemplates());
    }

    @PostMapping("/discipline-templates")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<DisciplineTemplateResponse> createDisciplineTemplate(
            @RequestBody @Valid CreateDisciplineTemplateRequest request
    ) {
        return ResponseEntity.ok(sportService.createDisciplineTemplate(request));
    }
}