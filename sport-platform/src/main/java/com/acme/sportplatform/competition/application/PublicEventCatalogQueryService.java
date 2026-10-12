package com.acme.sportplatform.competition.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.competition.api.PublicEventDetailsResponse;
import com.acme.sportplatform.competition.api.PublicEventDisciplineResponse;
import com.acme.sportplatform.competition.api.PublicEventListItemResponse;
import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.EventPublicLookup;
import com.acme.sportplatform.events.EventPublicLookupResult;

@Service
@Transactional(readOnly = true)
public class PublicEventCatalogQueryService {

    private final EventPublicLookup eventPublicLookup;
    private final EventDisciplineRepository eventDisciplineRepository;

    public PublicEventCatalogQueryService(
            EventPublicLookup eventPublicLookup,
            EventDisciplineRepository eventDisciplineRepository
    ) {
        this.eventPublicLookup = eventPublicLookup;
        this.eventDisciplineRepository = eventDisciplineRepository;
    }

    public List<PublicEventListItemResponse> list() {
        return eventPublicLookup
                .listPublicEvents()
                .stream()
                .map(this::toListItem)
                .toList();
    }

    public PublicEventDetailsResponse getByPublicSlug(
            String publicSlug
    ) {
        EventPublicLookupResult event =
                eventPublicLookup.getPublicBySlug(publicSlug);

        List<PublicEventDisciplineResponse> disciplines =
                eventDisciplineRepository
                        .findByEventIdAndStatus(
                                event.id(),
                                EventDisciplineStatus.PUBLISHED.name()
                        )
                        .stream()
                        .map(this::toDisciplineResponse)
                        .toList();

        return new PublicEventDetailsResponse(
                event.id(),
                event.sportId(),
                event.title(),
                event.description(),
                event.registrationOpenAt(),
                event.registrationCloseAt(),
                event.eventStartAt(),
                event.eventEndAt(),
                event.status(),
                event.publicSlug(),
                disciplines
        );
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

    private PublicEventDisciplineResponse toDisciplineResponse(
            EventDisciplineEntity discipline
    ) {
        return new PublicEventDisciplineResponse(
                discipline.getId(),
                discipline.getCode(),
                discipline.getName(),
                discipline.getCompetitionFormat(),
                discipline.getUnitType(),
                discipline.getResultType(),
                discipline.getRankingStrategy(),
                discipline.getParticipantLimit(),
                discipline.getEntryFeeAmount(),
                discipline.getEntryFeeCurrency(),
                discipline.getSettingsJson()
        );
    }
}