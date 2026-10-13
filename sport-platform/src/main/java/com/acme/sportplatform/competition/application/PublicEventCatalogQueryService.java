package com.acme.sportplatform.competition.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.competition.api.PublicEventListItemResponse;
import com.acme.sportplatform.events.EventPublicLookup;
import com.acme.sportplatform.events.EventPublicLookupResult;

@Service
@Transactional(readOnly = true)
public class PublicEventCatalogQueryService {

    private final EventPublicLookup eventPublicLookup;

    public PublicEventCatalogQueryService(
            EventPublicLookup eventPublicLookup
    ) {
        this.eventPublicLookup = eventPublicLookup;
    }

    public List<PublicEventListItemResponse> list() {
        return eventPublicLookup
                .listPublicEvents()
                .stream()
                .map(this::toListItem)
                .toList();
    }

    private PublicEventListItemResponse toListItem(
            EventPublicLookupResult event
    ) {
        return new PublicEventListItemResponse(
                event.id(),
                event.sportId(),
                event.title(),
                event.description(),
                event.registrationOpenAt(),
                event.registrationCloseAt(),
                event.eventStartAt(),
                event.eventEndAt(),
                event.status(),
                event.publicSlug()
        );
    }

}
