package com.acme.sportplatform.competition.application;

import org.springframework.stereotype.Component;

import com.acme.sportplatform.competition.api.EventDisciplineResponse;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;

@Component
public class EventDisciplineMapper {

    public EventDisciplineResponse toResponse(
            EventDisciplineEntity entity
    ) {
        return new EventDisciplineResponse(
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
                entity.getStatus(),
                entity.getSettingsJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}