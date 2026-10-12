package com.acme.sportplatform.results.application;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.results.api.CompetitionUnitDetailsResponse;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GetCompetitionUnitDetailsUseCase {

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final ResultRepository resultRepository;
    private final RegistrationLookup registrationLookup;
    private final ObjectMapper objectMapper;

    public GetCompetitionUnitDetailsUseCase(
            CompetitionUnitRepository competitionUnitRepository,
            CompetitionUnitEntryRepository entryRepository,
            ResultRepository resultRepository,
            RegistrationLookup registrationLookup,
            ObjectMapper objectMapper
    ) {
        this.competitionUnitRepository = competitionUnitRepository;
        this.entryRepository = entryRepository;
        this.resultRepository = resultRepository;
        this.registrationLookup = registrationLookup;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CompetitionUnitDetailsResponse execute(UUID unitId) {
        CompetitionUnitEntity unit = competitionUnitRepository
                .findById(unitId)
                .orElseThrow(() -> new BusinessException(
                        "results.unit_not_found",
                        "Competition unit not found: " + unitId
                ));

        List<CompetitionUnitEntryEntity> entries =
                entryRepository.findByCompetitionUnitId(unitId);

        List<CompetitionUnitDetailsResponse.EntryView> entryViews =
                entries.stream()
                        .map(this::toEntryView)
                        .collect(Collectors.toList());

        return new CompetitionUnitDetailsResponse(
                unit.getId(),
                unit.getEventDisciplineId(),
                unit.getLabel(),
                unit.getStatus(),
                entryViews
        );
    }

    private CompetitionUnitDetailsResponse.EntryView toEntryView(
            CompetitionUnitEntryEntity entry
    ) {
        ResultEntity result = resultRepository
                .findByCompetitionUnitEntryId(entry.getId())
                .orElse(null);

        RegistrationLookupResult registration = registrationLookup
                .findById(entry.getRegistrationId())
                .orElse(null);

        return new CompetitionUnitDetailsResponse.EntryView(
                entry.getId(),
                registration == null
                        ? fallbackParticipantName(entry.getRegistrationId())
                        : extractParticipantName(
                                registration.participantSnapshot(),
                                entry.getRegistrationId()
                        ),
                entry.getRegistrationId(),
                entry.getLaneOrPosition(),
                result != null ? result.getRawValue() : null,
                result != null ? result.getResultType() : null,
                result != null ? result.getStatus() : null,
                result != null ? result.getFinalPlace() : null
        );
    }

    private String extractParticipantName(
            String participantSnapshot,
            UUID registrationId
    ) {
        if (participantSnapshot == null || participantSnapshot.isBlank()) {
            return fallbackParticipantName(registrationId);
        }

        try {
            JsonNode snapshot = objectMapper.readTree(participantSnapshot);

            String firstName = snapshot.path("firstName").asText("").trim();
            String lastName = snapshot.path("lastName").asText("").trim();
            String middleName = snapshot.path("middleName").asText("").trim();

            String participantName = String.join(
                    " ",
                    List.of(lastName, firstName, middleName)
                            .stream()
                            .filter(value -> !value.isBlank())
                            .toList()
            );

            return participantName.isBlank()
                    ? fallbackParticipantName(registrationId)
                    : participantName;
        } catch (Exception exception) {
            return fallbackParticipantName(registrationId);
        }
    }

    private String fallbackParticipantName(UUID registrationId) {
        return "Участник "
                + registrationId.toString().substring(0, 8);
    }
}