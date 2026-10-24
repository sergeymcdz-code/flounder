package com.acme.sportplatform.results.application;

import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.results.ResultsProtocolLookup;
import com.acme.sportplatform.results.infrastructure.jpa.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ResultsProtocolLookupService implements ResultsProtocolLookup {
    private final CompetitionUnitRepository units;
    private final CompetitionUnitEntryRepository entries;
    private final ResultRepository results;
    private final RegistrationLookup registrations;
    private final ProfileLookup profiles;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public ResultsProtocolLookupService(CompetitionUnitRepository units, CompetitionUnitEntryRepository entries,
            ResultRepository results, RegistrationLookup registrations, ProfileLookup profiles, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.units = units;
        this.entries = entries;
        this.results = results;
        this.registrations = registrations;
        this.profiles = profiles;
        this.json = json;
    }

    private String snapshotClub(RegistrationLookupResult registration, ProfileLookupResult athlete) {
        try {
            var snapshot = json.readTree(registration.participantSnapshot() == null ? "{}" : registration.participantSnapshot());
            if (snapshot != null && snapshot.has("clubName"))
                return snapshot.get("clubName").isNull() ? null : snapshot.get("clubName").asText();
            // Legacy registrations without a snapshot field retain their existing protocol representation.
            return athlete.clubName();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new com.acme.sportplatform.common.exception.BusinessException("results.invalid_snapshot", "Некорректный снимок участника");
        }
    }

    @Override
    public List<Heat> heats(List<UUID> disciplineIds) {
        return heats(disciplineIds, false);
    }

    @Override
    public List<Heat> heats(List<UUID> disciplineIds, boolean publishedOnly) {
        if (disciplineIds.isEmpty()) return List.of();
        var heats = publishedOnly
                ? units.findByEventDisciplineIdInAndStatusInOrderBySequenceNumberAsc(disciplineIds, List.of("FINISHED", "COMPLETED"))
                : units.findByEventDisciplineIdInOrderBySequenceNumberAsc(disciplineIds);
        if (heats.isEmpty()) return List.of();
        var lanes = entries.findByCompetitionUnitIdIn(heats.stream().map(CompetitionUnitEntity::getId).toList());
        var registered = registrations.findByIdIn(lanes.stream().map(CompetitionUnitEntryEntity::getRegistrationId).toList())
                .stream().collect(Collectors.toMap(RegistrationLookupResult::id, r -> r));
        var athletes = profiles.findByIdIn(registered.values().stream()
                .map(RegistrationLookupResult::participantProfileId).distinct().toList()).stream()
                .collect(Collectors.toMap(ProfileLookupResult::id, p -> p));
        var outcomes = results.findByCompetitionUnitEntryIdIn(lanes.stream().map(CompetitionUnitEntryEntity::getId).toList())
                .stream().collect(Collectors.toMap(ResultEntity::getCompetitionUnitEntryId, r -> r));
        var byHeat = lanes.stream().collect(Collectors.groupingBy(CompetitionUnitEntryEntity::getCompetitionUnitId));
        return heats.stream().map(h -> new Heat(h.getEventDisciplineId(), h.getSequenceNumber(),
                h.getAgeGroupCode(), h.getStatus(), h.getScheduledAt(),
                byHeat.getOrDefault(h.getId(), List.of()).stream()
                        .sorted(java.util.Comparator.comparing(CompetitionUnitEntryEntity::getLaneOrPosition,
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                        .map(e -> {
                            var registration = registered.get(e.getRegistrationId());
                            var athlete = athletes.get(registration.participantProfileId());
                            var result = outcomes.get(e.getId());
                            return new Entry(e.getLaneOrPosition(), athlete.lastName(), athlete.firstName(),
                                    athlete.birthDate() == null ? null : athlete.birthDate().getYear(), athlete.gender(),
                                    snapshotClub(registration, athlete), e.getSeedTimeMs() == null ? registration.seedTimeMs() : e.getSeedTimeMs(),
                                    "WITHDRAWN".equals(e.getEntryStatus()) || "WITHDRAWN".equals(registration.status()),
                                    result == null ? null : new Outcome(result.getTimeMs(), result.getStatus(),
                                            result.getRawValue(), result.getFinalPlace(), result.getPlaceInAgeGroup()), athlete.id());
                        }).toList(), h.isOfficial())).toList();
    }
}
