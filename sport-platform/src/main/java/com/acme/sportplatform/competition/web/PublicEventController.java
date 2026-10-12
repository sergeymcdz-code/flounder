package com.acme.sportplatform.competition.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.competition.api.PublicEventDetailsResponse;
import com.acme.sportplatform.competition.api.PublicEventListItemResponse;
import com.acme.sportplatform.competition.application.PublicEventCatalogQueryService;

@RestController
@RequestMapping("/api/v1/public/events")
public class PublicEventController {

    private final PublicEventCatalogQueryService publicEventCatalogQueryService;

    public PublicEventController(
            PublicEventCatalogQueryService publicEventCatalogQueryService
    ) {
        this.publicEventCatalogQueryService =
                publicEventCatalogQueryService;
    }

    @GetMapping
    public ResponseEntity<List<PublicEventListItemResponse>> list() {
        return ResponseEntity.ok(
                publicEventCatalogQueryService.list()
        );
    }

    @GetMapping("/{publicSlug}")
    public ResponseEntity<PublicEventDetailsResponse> getByPublicSlug(
            @PathVariable String publicSlug
    ) {
        return ResponseEntity.ok(
                publicEventCatalogQueryService.getByPublicSlug(
                        publicSlug
                )
        );
    }
}