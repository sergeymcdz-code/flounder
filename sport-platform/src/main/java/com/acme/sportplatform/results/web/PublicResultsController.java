package com.acme.sportplatform.results.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.results.api.PublicDisciplineResultsResponse;
import com.acme.sportplatform.results.application.GetPublicDisciplineResultsUseCase;

@RestController
@RequestMapping("/api/v1/events")
public class PublicResultsController {

    private final GetPublicDisciplineResultsUseCase
            getPublicDisciplineResultsUseCase;

    public PublicResultsController(
            GetPublicDisciplineResultsUseCase getPublicDisciplineResultsUseCase
    ) {
        this.getPublicDisciplineResultsUseCase =
                getPublicDisciplineResultsUseCase;
    }

    @GetMapping("/{eventId}/disciplines/{disciplineId}/results")
    public ResponseEntity<PublicDisciplineResultsResponse> getResults(
            @PathVariable UUID eventId,
            @PathVariable UUID disciplineId
    ) {
        return ResponseEntity.ok(
                getPublicDisciplineResultsUseCase.execute(
                        eventId,
                        disciplineId
                )
        );
    }
}