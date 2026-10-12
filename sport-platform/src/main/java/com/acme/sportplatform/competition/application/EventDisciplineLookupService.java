package com.acme.sportplatform.competition.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineLookupResult;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;

@Service
@Transactional(readOnly = true)
public class EventDisciplineLookupService implements EventDisciplineLookup {

    private final EventDisciplineRepository eventDisciplineRepository;

    public EventDisciplineLookupService(
            EventDisciplineRepository eventDisciplineRepository
    ) {
        this.eventDisciplineRepository = eventDisciplineRepository;
    }

    @Override
    public EventDisciplineLookupResult getPublishedById(
            UUID eventDisciplineId
    ) {
        EventDisciplineEntity entity = eventDisciplineRepository
                .findById(eventDisciplineId)
                .filter(discipline -> EventDisciplineStatus.PUBLISHED.name()
                        .equals(discipline.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Published event discipline not found: "
                                + eventDisciplineId
                ));

        return new EventDisciplineLookupResult(
                entity.getId(),
                entity.getEventId(),
                entity.getDisciplineTemplateId(),
                entity.getCode(),
                entity.getName(),
                entity.getCompetitionFormat(),
                entity.getUnitType(),
                entity.getResultType(),
                entity.getRankingStrategy(),
                entity.getParticipantLimit(),
                entity.getEntryFeeAmount(),
                entity.getEntryFeeCurrency(),
                entity.getSettingsJson()
        );
    }

    @Override
    public List<EventDisciplineSummary> findByEventId(UUID eventId) {
        return eventDisciplineRepository.findByEventId(eventId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<EventDisciplineSummary> findPublishedByEventId(
            UUID eventId
    ) {
        return eventDisciplineRepository.findByEventIdAndStatus(
                        eventId,
                        EventDisciplineStatus.PUBLISHED.name()
                )
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Optional<EventDisciplineSummary> findById(
            UUID eventDisciplineId
    ) {
        return eventDisciplineRepository.findById(eventDisciplineId)
                .map(this::toSummary);
    }

    private EventDisciplineSummary toSummary(
            EventDisciplineEntity entity
    ) {
        return new EventDisciplineSummary(
                entity.getId(),
                entity.getEventId(),
                entity.getCode(),
                entity.getName(),
                entity.getCompetitionFormat(),
                entity.getUnitType(),
                entity.getResultType(),
                entity.getRankingStrategy(),
                entity.getParticipantLimit(),
                entity.getEntryFeeAmount(),
                entity.getEntryFeeCurrency(),
                entity.getSettingsJson()
        );
    }
}