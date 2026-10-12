package com.acme.sportplatform.results.application;

import com.acme.sportplatform.common.TimeParser;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventManagementAccess;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.results.api.*;
import com.acme.sportplatform.results.domain.ResultStatus;
import com.acme.sportplatform.results.infrastructure.jpa.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class SwimResultsUseCase {
    private final CompetitionUnitRepository units;
    private final CompetitionUnitEntryRepository entries;
    private final ResultRepository results;
    private final EventDisciplineLookup disciplines;
    private final RegistrationLookup registrations;
    private final EventManagementAccess access;
    private final RecordResultUseCase record;
    private final GetCompetitionUnitDetailsUseCase details;
    private final ObjectMapper json;

    public SwimResultsUseCase(
            CompetitionUnitRepository units,
            CompetitionUnitEntryRepository entries,
            ResultRepository results,
            EventDisciplineLookup disciplines,
            RegistrationLookup registrations,
            EventManagementAccess access,
            RecordResultUseCase record,
            GetCompetitionUnitDetailsUseCase details,
            ObjectMapper json) {
        this.units = units;
        this.entries = entries;
        this.results = results;
        this.disciplines = disciplines;
        this.registrations = registrations;
        this.access = access;
        this.record = record;
        this.details = details;
        this.json = json;
    }

    public UUID unitIdForEntry(UUID id) {
        return entries.findById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "results.entry_not_found", "Участник заплыва не найден"))
                .getCompetitionUnitId();
    }

    public CompetitionUnitDetailsResponse save(
            UUID unitId, UUID userId, List<SwimResultRequest> requests) {
        var unit =
                units.findById(unitId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "results.unit_not_found", "Заплыв не найден"));
        var discipline =
                disciplines
                        .findById(unit.getEventDisciplineId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "competition.discipline_not_found",
                                                "Дисциплина не найдена"));
        access.assertCanManage(discipline.eventId());
        Set<UUID> seen = new HashSet<>();
        for (var r : requests) {
            if (!seen.add(r.competitionUnitEntryId()))
                throw new BusinessException("results.duplicate_entry", "Участник указан дважды");
            String status =
                    switch (r.status()) {
                        case "VALID" -> "PENDING";
                        case "DNS" -> "DID_NOT_START";
                        case "DNF" -> "DID_NOT_FINISH";
                        case "DSQ" -> "DISQUALIFIED";
                        default ->
                                throw new BusinessException(
                                        "results.invalid_status", "Недопустимый статус результата");
                    };
            Long time = r.timeMs();
            if (r.time() != null) {
                long parsed = TimeParser.parse(r.time());
                if (time != null && time != parsed)
                    throw new BusinessException(
                            "results.time_mismatch", "Время в строке и миллисекундах не совпадает");
                time = parsed;
            }
            if ("VALID".equals(r.status()) && (time == null || time <= 0))
                throw new BusinessException(
                        "results.time_required",
                        "Укажите положительное время для результата VALID");
            record.execute(
                    new RecordResultRequest(
                            r.competitionUnitEntryId(),
                            time == null ? null : TimeParser.format(time),
                            status),
                    "TIME",
                    userId,
                    unitId);
            if ("VALID".equals(r.status())) {
                var result =
                        results.findByCompetitionUnitEntryId(r.competitionUnitEntryId())
                                .orElseThrow();
                result.setTimeMs(time);
                result.setStatus(ResultStatus.VALID.name());
            }
        }
        rankDiscipline(unit.getEventDisciplineId());
        return details.execute(unitId);
    }

    public List<CompetitionUnitDetailsResponse> protocol(UUID disciplineId) {
        var discipline =
                disciplines
                        .findById(disciplineId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "competition.discipline_not_found",
                                                "Дисциплина не найдена"));
        access.assertCanManage(discipline.eventId());
        return units.findByEventDisciplineIdOrderBySequenceNumberAsc(disciplineId).stream()
                .map(u -> details.execute(u.getId()))
                .toList();
    }

    private void rankDiscipline(UUID disciplineId) {
        // TODO T02: replace with ResultRanker once T02 is merged.
        var discipline = disciplines.findById(disciplineId).orElseThrow();
        List<ResultEntity> all = new ArrayList<>();
        Map<UUID, String> groups = new HashMap<>();
        for (var unit : units.findByEventDisciplineIdOrderBySequenceNumberAsc(disciplineId)) {
            for (var entry : entries.findByCompetitionUnitId(unit.getId())) {
                var result = results.findByCompetitionUnitEntryId(entry.getId()).orElse(null);
                if (result == null) continue;
                result.setFinalPlace(null);
                result.setPlaceInAgeGroup(null);
                all.add(result);
                String group = unit.getAgeGroupCode();
                if (!"BY_AGE_GROUP".equals(discipline.seedingMode()) || group == null) {
                    try {
                        var snapshot =
                                json.readTree(
                                        registrations
                                                .getById(entry.getRegistrationId())
                                                .participantSnapshot());
                        int year =
                                java.time.LocalDate.parse(snapshot.path("birthDate").asText())
                                        .getYear();
                        String gender =
                                switch (snapshot.path("gender").asText()) {
                                    case "MALE" -> "M";
                                    case "FEMALE" -> "F";
                                    default -> snapshot.path("gender").asText();
                                };
                        group =
                                discipline.ageGroups().stream()
                                        .filter(g -> g.includes(gender, year))
                                        .map(g -> g.code())
                                        .findFirst()
                                        .orElse(null);
                    } catch (Exception ex) {
                        throw new BusinessException(
                                "results.invalid_athlete_snapshot",
                                "Некорректные данные спортсмена в заявке");
                    }
                }
                if (group != null) groups.put(result.getId(), group);
            }
        }
        var valid =
                all.stream()
                        .filter(r -> "VALID".equals(r.getStatus()) && r.getTimeMs() != null)
                        .sorted(Comparator.comparing(ResultEntity::getTimeMs))
                        .toList();
        rank(valid, false);
        Map<String, List<ResultEntity>> byGroup = new HashMap<>();
        for (var r : valid)
            if (groups.containsKey(r.getId()))
                byGroup.computeIfAbsent(groups.get(r.getId()), k -> new ArrayList<>()).add(r);
        byGroup.values().forEach(list -> rank(list, true));
        results.saveAll(all);
    }

    private void rank(List<ResultEntity> sorted, boolean age) {
        Long previous = null;
        int place = 0;
        for (int i = 0; i < sorted.size(); i++) {
            var r = sorted.get(i);
            if (!r.getTimeMs().equals(previous)) place = i + 1;
            previous = r.getTimeMs();
            if (age) r.setPlaceInAgeGroup(place);
            else r.setFinalPlace(place);
        }
    }
}
