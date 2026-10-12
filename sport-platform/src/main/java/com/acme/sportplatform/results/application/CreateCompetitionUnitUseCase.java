package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.results.api.CompetitionUnitResponse;
import com.acme.sportplatform.results.api.CreateCompetitionUnitRequest;
import com.acme.sportplatform.results.domain.CompetitionUnitStatus;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;

@Service
public class CreateCompetitionUnitUseCase {

    private final CompetitionUnitRepository competitionUnitRepository;
    private final EventDisciplineLookup eventDisciplineLookup;

    public CreateCompetitionUnitUseCase(
            CompetitionUnitRepository competitionUnitRepository,
            EventDisciplineLookup eventDisciplineLookup
    ) {
        this.competitionUnitRepository = competitionUnitRepository;
        this.eventDisciplineLookup = eventDisciplineLookup;
    }

    @Transactional
    public CompetitionUnitResponse execute(CreateCompetitionUnitRequest request) {
        try {
            eventDisciplineLookup.getPublishedById(request.eventDisciplineId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("results.event_discipline_not_found", "Дисциплина события не найдена");
        }

        CompetitionUnitEntity entity = new CompetitionUnitEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventDisciplineId(request.eventDisciplineId());
        entity.setLabel(request.label());
        entity.setSequenceNumber(request.sequenceNumber());
        entity.setStatus(CompetitionUnitStatus.DRAFT.name());

        OffsetDateTime now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        CompetitionUnitEntity saved = competitionUnitRepository.save(entity);

        return new CompetitionUnitResponse(
                saved.getId(),
                saved.getEventDisciplineId(),
                saved.getLabel(),
                saved.getSequenceNumber(),
                saved.getStatus(),
                saved.getScheduledAt()
        );
    }
}