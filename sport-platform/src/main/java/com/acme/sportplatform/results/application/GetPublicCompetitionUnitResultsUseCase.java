package com.acme.sportplatform.results.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.results.api.PublicCompetitionUnitResultsResponse;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GetPublicCompetitionUnitResultsUseCase {

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final ResultRepository resultRepository;
    private final RegistrationLookup registrationLookup;
    private final ObjectMapper objectMapper;

    public GetPublicCompetitionUnitResultsUseCase(
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
    public PublicCompetitionUnitResultsResponse execute(UUID unitId) {
        CompetitionUnitEntity unit = competitionUnitRepository
                .findById(unitId)
                .orElseThrow(this::publicNotFound);

        if (!"PUBLISHED".equals(unit.getStatus())) {
            throw publicNotFound();
        }

        List<CompetitionUnitEntryEntity> entries =
                entryRepository.findByCompetitionUnitId(unitId);

        List<UUID> entryIds = entries.stream()
                .map(CompetitionUnitEntryEntity::getId)
                .toList();

        List<UUID> registrationIds = entries.stream()
                .map(CompetitionUnitEntryEntity::getRegistrationId)
                .distinct()
                .toList();

        Map<UUID, ResultEntity> resultByEntryId =
                resultRepository.findByCompetitionUnitEntryIdIn(entryIds)
                        .stream()
                        .collect(Collectors.toMap(
                                ResultEntity::getCompetitionUnitEntryId,
                                Function.identity()
                        ));

        Map<UUID, RegistrationLookupResult> registrationById =
                registrationLookup.findByIdIn(registrationIds)
                        .stream()
                        .collect(Collectors.toMap(
                                RegistrationLookupResult::id,
                                Function.identity()
                        ));

        List<PublicCompetitionUnitResultsResponse.EntryView> entryViews =
                entries.stream()
                        .map(entry -> toEntryView(
                                entry,
                                resultByEntryId.get(entry.getId()),
                                registrationById.get(entry.getRegistrationId())
                        ))
                        .sorted(publicEntryComparator())
                        .toList();

        return new PublicCompetitionUnitResultsResponse(
                unit.getId(),
                unit.getEventDisciplineId(),
                unit.getLabel(),
                entryViews
        );
    }

    private Comparator<PublicCompetitionUnitResultsResponse.EntryView>
    publicEntryComparator() {
        return Comparator
                .comparing(
                        PublicCompetitionUnitResultsResponse.EntryView::finalPlace,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(
                        PublicCompetitionUnitResultsResponse.EntryView::laneOrPosition,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(
                        PublicCompetitionUnitResultsResponse.EntryView::participantName,
                        String.CASE_INSENSITIVE_ORDER
                );
    }

    private PublicCompetitionUnitResultsResponse.EntryView toEntryView(
            CompetitionUnitEntryEntity entry,
            ResultEntity result,
            RegistrationLookupResult registration
    ) {
        return new PublicCompetitionUnitResultsResponse.EntryView(
                registration == null
                        ? fallbackParticipantName(entry.getRegistrationId())
                        : extractParticipantName(
                                registration.participantSnapshot(),
                                entry.getRegistrationId()
                        ),
                entry.getLaneOrPosition(),
                result == null ? null : result.getRawValue(),
                result == null ? null : result.getResultType(),
                result == null ? null : result.getStatus(),
                result == null ? null : result.getFinalPlace()
        );
    }

    private BusinessException publicNotFound() {
        return new BusinessException(
                "results.public_not_found",
                "Published results were not found."
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