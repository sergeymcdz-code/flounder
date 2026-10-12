package com.acme.sportplatform.competition.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.api.*;
import com.acme.sportplatform.competition.infrastructure.jpa.*;
import com.acme.sportplatform.events.EventManagementAccess;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class UpdateEventDisciplineUseCase {
    private final EventDisciplineRepository disciplines;
    private final EventDisciplineMapper mapper;
    private final SwimDisciplineValidator swim;
    private final EventManagementAccess access;
    private final com.acme.sportplatform.competition.RegistrationUsageLookup registrations;
    private final com.acme.sportplatform.competition.CompetitionUnitUsageLookup units;

    public UpdateEventDisciplineUseCase(
            EventDisciplineRepository disciplines,
            EventDisciplineMapper mapper,
            SwimDisciplineValidator swim,
            EventManagementAccess access,
            com.acme.sportplatform.competition.RegistrationUsageLookup registrations,
            com.acme.sportplatform.competition.CompetitionUnitUsageLookup units) {
        this.disciplines = disciplines;
        this.mapper = mapper;
        this.swim = swim;
        this.access = access;
        this.registrations = registrations;
        this.units = units;
    }

    public EventDisciplineResponse patch(
            UUID id, CreateEventDisciplineRequest r, java.util.Set<String> clearFields) {
        var d = require(id);
        access.assertCanManage(d.getEventId());
        if (clearFields.contains("sessionId")) d.setSessionId(null);
        if (clearFields.contains("orderInSession")) d.setOrderInSession(null);
        if (clearFields.contains("heatDurationSec")) d.setHeatDurationSec(null);
        if (clearFields.contains("participantLimit")) d.setParticipantLimit(null);
        swim.apply(d, r);
        if (r.code() != null) {
            if (r.code().isBlank())
                throw new BusinessException("competition.invalid_code", "Код не может быть пустым");
            if (disciplines
                    .findByEventIdAndCode(d.getEventId(), r.code())
                    .filter(other -> !other.getId().equals(id))
                    .isPresent())
                throw new BusinessException(
                        "competition.discipline_code_already_exists",
                        "Код дисциплины уже существует");
            d.setCode(r.code());
        }
        if (r.name() != null) d.setName(r.name().isBlank() ? swim.generatedName(d) : r.name());
        if (r.participantLimit() != null) d.setParticipantLimit(r.participantLimit());
        if (r.entryFeeAmount() != null) d.setEntryFeeAmount(r.entryFeeAmount());
        if (r.entryFeeCurrency() != null) d.setEntryFeeCurrency(r.entryFeeCurrency());
        if (r.settingsJson() != null) d.setSettingsJson(r.settingsJson().toString());
        d.setUpdatedAt(OffsetDateTime.now());
        return mapper.toResponse(disciplines.save(d));
    }

    public void delete(UUID id) {
        var d = require(id);
        access.assertCanManage(d.getEventId());
        if (registrations.isInUse(id))
            throw new BusinessException(
                    "competition.discipline_in_use",
                    "Дисциплина содержит заявки и не может быть удалена");
        if (units.isInUse(id))
            throw new BusinessException(
                    "competition.discipline_in_use",
                    "Дисциплина содержит заплывы и не может быть удалена");
        disciplines.delete(d);
    }

    private EventDisciplineEntity require(UUID id) {
        return disciplines
                .findById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "competition.discipline_not_found",
                                        "Дисциплина не найдена"));
    }
}
