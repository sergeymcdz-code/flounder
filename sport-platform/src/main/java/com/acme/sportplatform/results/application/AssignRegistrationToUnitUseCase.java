package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;

@Service
public class AssignRegistrationToUnitUseCase {

    private static final String CONFIRMED = "CONFIRMED";

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final RegistrationLookup registrationLookup;

    public AssignRegistrationToUnitUseCase(
            CompetitionUnitRepository competitionUnitRepository,
            CompetitionUnitEntryRepository entryRepository,
            RegistrationLookup registrationLookup
    ) {
        this.competitionUnitRepository = competitionUnitRepository;
        this.entryRepository = entryRepository;
        this.registrationLookup = registrationLookup;
    }

    @Transactional
    public UUID execute(
            UUID competitionUnitId,
            UUID registrationId,
            Integer laneOrPosition
    ) {
        CompetitionUnitEntity competitionUnit = competitionUnitRepository
                .findById(competitionUnitId)
                .orElseThrow(() -> new BusinessException(
                        "results.competition_unit_not_found",
                        "Заплыв/забег не найден"
                ));

        RegistrationLookupResult registration = registrationLookup
                .findById(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "results.registration_not_found",
                        "Заявка не найдена"
                ));

        if (!CONFIRMED.equals(registration.status())) {
            throw new BusinessException(
                    "results.registration_not_confirmed",
                    "В соревнование можно назначить только подтверждённую заявку"
            );
        }

        if (!competitionUnit.getEventDisciplineId().equals(
                registration.eventDisciplineId()
        )) {
            throw new BusinessException(
                    "results.registration_discipline_mismatch",
                    "Заявка относится к другой дисциплине"
            );
        }

        if (entryRepository.existsByCompetitionUnitIdAndRegistrationId(
                competitionUnitId,
                registrationId
        )) {
            throw new BusinessException(
                    "results.entry_already_exists",
                    "Участник уже назначен в этот заплыв"
            );
        }

        if (laneOrPosition != null
                && entryRepository
                        .existsByCompetitionUnitIdAndLaneOrPosition(
                                competitionUnitId,
                                laneOrPosition
                        )) {
            throw new BusinessException(
                    "results.lane_already_taken",
                    "Эта дорожка или стартовая позиция уже занята"
            );
        }

        CompetitionUnitEntryEntity entry =
                new CompetitionUnitEntryEntity();

        entry.setId(UUID.randomUUID());
        entry.setCompetitionUnitId(competitionUnitId);
        entry.setRegistrationId(registrationId);
        entry.setLaneOrPosition(laneOrPosition);
        entry.setCreatedAt(OffsetDateTime.now());

        return entryRepository.save(entry).getId();
    }
}