package com.acme.sportplatform.competition.web;

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

import com.acme.sportplatform.competition.api.CreateEventDisciplineRequest;
import com.acme.sportplatform.competition.api.EventDisciplineResponse;
import com.acme.sportplatform.competition.application.CreateEventDisciplineUseCase;
import com.acme.sportplatform.competition.application.ListEventDisciplinesUseCase;
import com.acme.sportplatform.competition.application.PublishEventDisciplineUseCase;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/events/{eventId}/disciplines")
public class CompetitionController {

        private final CreateEventDisciplineUseCase createEventDisciplineUseCase;
        private final ListEventDisciplinesUseCase listEventDisciplinesUseCase;
        private final PublishEventDisciplineUseCase publishEventDisciplineUseCase;

        public CompetitionController(
                CreateEventDisciplineUseCase createEventDisciplineUseCase,
                ListEventDisciplinesUseCase listEventDisciplinesUseCase,
                PublishEventDisciplineUseCase publishEventDisciplineUseCase
        ) {
                this.createEventDisciplineUseCase = createEventDisciplineUseCase;
                this.listEventDisciplinesUseCase = listEventDisciplinesUseCase;
                this.publishEventDisciplineUseCase = publishEventDisciplineUseCase;
        }

        @PostMapping
        @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
        public ResponseEntity<EventDisciplineResponse> create(
                @PathVariable UUID eventId,
                @RequestBody @Valid CreateEventDisciplineRequest request
        ) {
                return ResponseEntity.ok(
                        createEventDisciplineUseCase.execute(eventId, request)
                );
        }

        @GetMapping
        @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
        public ResponseEntity<List<EventDisciplineResponse>> list(
                @PathVariable UUID eventId
        ) {
                return ResponseEntity.ok(
                        listEventDisciplinesUseCase.execute(eventId)
                );
        }

        @PostMapping("/{disciplineId}/publish")
        @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('ORGANIZER')")
        public ResponseEntity<EventDisciplineResponse> publish(
                @PathVariable UUID eventId,
                @PathVariable UUID disciplineId
        ) {
        return ResponseEntity.ok(
                publishEventDisciplineUseCase.execute(eventId, disciplineId)
                );
        }
}