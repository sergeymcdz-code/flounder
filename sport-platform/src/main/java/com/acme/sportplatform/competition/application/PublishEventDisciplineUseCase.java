package com.acme.sportplatform.competition.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.api.EventDisciplineResponse;
import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;

@Service
public class PublishEventDisciplineUseCase {

    private final EventDisciplineRepository eventDisciplineRepository;
    private final EventDisciplineMapper eventDisciplineMapper;

    public PublishEventDisciplineUseCase(
            EventDisciplineRepository eventDisciplineRepository,
            EventDisciplineMapper eventDisciplineMapper
    ) {
        this.eventDisciplineRepository = eventDisciplineRepository;
        this.eventDisciplineMapper = eventDisciplineMapper;
    }

    @Transactional
    public EventDisciplineResponse execute(
            UUID eventId,
            UUID disciplineId
    ) {
        EventDisciplineEntity discipline = eventDisciplineRepository
                .findById(disciplineId)
                .orElseThrow(() -> new BusinessException(
                        "competition.discipline_not_found",
                        "Event discipline not found"
                ));

        if (!discipline.getEventId().equals(eventId)) {
            throw new BusinessException(
                    "competition.discipline_event_mismatch",
                    "Event discipline does not belong to this event"
            );
        }

        if (!EventDisciplineStatus.DRAFT.name()
                .equals(discipline.getStatus())) {
            throw new BusinessException(
                    "competition.discipline_not_draft",
                    "Only a draft discipline can be published"
            );
        }

        discipline.setStatus(EventDisciplineStatus.PUBLISHED.name());
        discipline.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return eventDisciplineMapper.toResponse(
                eventDisciplineRepository.save(discipline)
        );
    }
}