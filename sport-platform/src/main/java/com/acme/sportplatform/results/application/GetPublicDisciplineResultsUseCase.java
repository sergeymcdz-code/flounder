package com.acme.sportplatform.results.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.results.api.PublicDisciplineResultsResponse;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GetPublicDisciplineResultsUseCase {

    private static final String PUBLISHED = "PUBLISHED";

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final ResultRepository resultRepository;
    private final RegistrationLookup registrationLookup;
    private final ObjectMapper objectMapper;

    public GetPublicDisciplineResultsUseCase(
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
    public PublicDisciplineResultsResponse execute(
            UUID eventId,
            UUID eventDisciplineId
    ) {
        List<CompetitionUnitEntity> units = competitionUnitRepository
                .findByEventDisciplineIdAndStatusOrderBySequenceNumberAsc(
                        eventDisciplineId,
                        PUBLISHED
                );

        if (units.isEmpty()) {
            return new PublicDisciplineResultsResponse(
                    eventId,
                    eventDisciplineId,
                    List.of()
            );
        }

        List<UUID> unitIds = units.stream()
                .map(CompetitionUnitEntity::getId)
                .toList();

        List<CompetitionUnitEntryEntity> allEntries =
                entryRepository.findByCompetitionUnitIdIn(unitIds);

        List<UUID> entryIds = allEntries.stream()
                .map(CompetitionUnitEntryEntity::getId)
                .toList();

        List<UUID> registrationIds = allEntries.stream()
                .map(CompetitionUnitEntryEntity::getRegistrationId)
                .distinct()
                .toList();

        Map<UUID, List<CompetitionUnitEntryEntity>> entriesByUnitId =
                allEntries.stream()
                        .collect(Collectors.groupingBy(
                                CompetitionUnitEntryEntity::getCompetitionUnitId
                        ));

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

        List<PublicDisciplineResultsResponse.UnitView> unitViews =
                units.stream()
                        .map(unit -> toUnitView(
                                unit,
                                entriesByUnitId.getOrDefault(
                                        unit.getId(),
                                        List.of()
                                ),
                                resultByEntryId,
                                registrationById
                        ))
                        .toList();

        return new PublicDisciplineResultsResponse(
                eventId,
                eventDisciplineId,
                unitViews
        );
    }

    private PublicDisciplineResultsResponse.UnitView toUnitView(
            CompetitionUnitEntity unit,
            List<CompetitionUnitEntryEntity> entries,
            Map<UUID, ResultEntity> resultByEntryId,
            Map<UUID, RegistrationLookupResult> registrationById
    ) {
        List<PublicDisciplineResultsResponse.EntryView> entryViews =
                entries.stream()
                        .map(entry -> toEntryView(
                                entry,
                                resultByEntryId.get(entry.getId()),
                                registrationById.get(entry.getRegistrationId())
                        ))
                        .sorted(
                                Comparator
                                        .comparing(
                                                PublicDisciplineResultsResponse
                                                        .EntryView::place,
                                                Comparator.nullsLast(
                                                        Comparator.naturalOrder()
                                                )
                                        )
                                        .thenComparing(
                                                PublicDisciplineResultsResponse
                                                        .EntryView::participantName,
                                                String.CASE_INSENSITIVE_ORDER
                                        )
                        )
                        .toList();

        return new PublicDisciplineResultsResponse.UnitView(
                unit.getId(),
                unit.getLabel(),
                unit.getSequenceNumber(),
                entryViews
        );
    }

    private PublicDisciplineResultsResponse.EntryView toEntryView(
            CompetitionUnitEntryEntity entry,
            ResultEntity result,
            RegistrationLookupResult registration
    ) {
        String participantSnapshot = registration == null
                ? null
                : registration.participantSnapshot();

        return new PublicDisciplineResultsResponse.EntryView(
                result == null ? null : result.getFinalPlace(),
                participantName(participantSnapshot),
                clubName(participantSnapshot),
                result == null ? null : result.getRawValue(),
                result == null ? null : result.getResultType(),
                result == null ? "PENDING" : result.getStatus()
        );
    }

    private String participantName(String participantSnapshot) {
        if (participantSnapshot == null || participantSnapshot.isBlank()) {
            return "Участник";
        }

        try {
            JsonNode snapshot = objectMapper.readTree(participantSnapshot);

            String lastName = snapshot.path("lastName").asText("").trim();
            String firstName = snapshot.path("firstName").asText("").trim();
            String middleName = snapshot.path("middleName").asText("").trim();

            String name = String.join(
                    " ",
                    List.of(lastName, firstName, middleName)
                            .stream()
                            .filter(value -> !value.isBlank())
                            .toList()
            );

            return name.isBlank() ? "Участник" : name;
        } catch (Exception exception) {
            return "Участник";
        }
    }

    private String clubName(String participantSnapshot) {
        if (participantSnapshot == null || participantSnapshot.isBlank()) {
            return null;
        }

        try {
            JsonNode snapshot = objectMapper.readTree(participantSnapshot);
            String clubName = snapshot.path("clubName").asText("").trim();

            return clubName.isBlank() ? null : clubName;
        } catch (Exception exception) {
            return null;
        }
    }
}