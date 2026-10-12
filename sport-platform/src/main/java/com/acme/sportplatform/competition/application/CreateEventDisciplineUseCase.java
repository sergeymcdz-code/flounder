package com.acme.sportplatform.competition.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.api.CreateEventDisciplineRequest;
import com.acme.sportplatform.competition.api.EventDisciplineResponse;
import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventLookupResult;
import com.acme.sportplatform.sports.DisciplineTemplateLookup;
import com.acme.sportplatform.sports.DisciplineTemplateLookupResult;

@Service
public class CreateEventDisciplineUseCase {

    private final EventDisciplineRepository eventDisciplineRepository;
    private final EventLookup eventLookup;
    private final DisciplineTemplateLookup disciplineTemplateLookup;
    private final EventDisciplineMapper eventDisciplineMapper;

    public CreateEventDisciplineUseCase(
            EventDisciplineRepository eventDisciplineRepository,
            EventLookup eventLookup,
            DisciplineTemplateLookup disciplineTemplateLookup,
            EventDisciplineMapper eventDisciplineMapper
    ) {
        this.eventDisciplineRepository = eventDisciplineRepository;
        this.eventLookup = eventLookup;
        this.disciplineTemplateLookup = disciplineTemplateLookup;
        this.eventDisciplineMapper = eventDisciplineMapper;
    }

    @Transactional
    public EventDisciplineResponse execute(
            UUID eventId,
            CreateEventDisciplineRequest request
    ) {
        EventLookupResult event;

        try {
            event = eventLookup.getById(eventId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "competition.event_not_found",
                    "Event not found"
            );
        }

        if (!"DRAFT".equals(event.status())) {
            throw new BusinessException(
                    "competition.event_not_editable",
                    "Disciplines can only be configured for a draft event"
            );
        }

        DisciplineTemplateLookupResult template;

        try {
            template = disciplineTemplateLookup.getById(
                    request.disciplineTemplateId()
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "competition.discipline_template_not_found",
                    "Discipline template not found"
            );
        }

        if (!event.sportId().equals(template.sportId())) {
            throw new BusinessException(
                    "competition.sport_mismatch",
                    "Event and discipline template must belong to the same sport"
            );
        }

        if (eventDisciplineRepository.existsByEventIdAndCode(
                eventId,
                request.code()
        )) {
            throw new BusinessException(
                    "competition.discipline_code_already_exists",
                    "Discipline code already exists for this event"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventDisciplineEntity entity = new EventDisciplineEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventId(eventId);
        entity.setDisciplineTemplateId(template.id());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setCompetitionFormat(template.competitionFormat());
        entity.setUnitType(template.unitType());
        entity.setResultType(template.resultType());
        entity.setRankingStrategy(template.rankingStrategy());
        entity.setParticipantLimit(request.participantLimit());
        entity.setEntryFeeAmount(
        request.entryFeeAmount() == null
                ? BigDecimal.ZERO
                : request.entryFeeAmount()
        );

        entity.setEntryFeeCurrency(
                request.entryFeeCurrency() == null
                        ? "RUB"
                        : request.entryFeeCurrency()
        );
        entity.setStatus(EventDisciplineStatus.DRAFT.name());

        String settingsJson = request.settingsJson() == null
                ? template.defaultMeta()
                : request.settingsJson().toString();;

        entity.setSettingsJson(settingsJson);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return eventDisciplineMapper.toResponse(
        eventDisciplineRepository.save(entity)
        );
    }
}