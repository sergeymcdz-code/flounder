package com.acme.sportplatform.results.application;

import com.acme.sportplatform.common.TimeParser;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.results.api.*;
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
    private final EventAccessService access;
    private final com.acme.sportplatform.results.RuleExecutionLog logs;
    private final GetCompetitionUnitDetailsUseCase details;
    private final ObjectMapper json;

    public SwimResultsUseCase(
            CompetitionUnitRepository units,
            CompetitionUnitEntryRepository entries,
            ResultRepository results,
            EventDisciplineLookup disciplines,
            RegistrationLookup registrations,
            EventAccessService access,
            com.acme.sportplatform.results.RuleExecutionLog logs,
            GetCompetitionUnitDetailsUseCase details,
            ObjectMapper json) {
        this.units = units;
        this.entries = entries;
        this.results = results;
        this.disciplines = disciplines;
        this.registrations = registrations;
        this.access = access;
        this.logs = logs;
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
            UUID unitId,
            UUID userId,
            List<SwimResultRequest> requests,
            com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        long started = System.nanoTime();
        var unit =
                units.lockById(unitId)
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
        access.assertCanManage(discipline.eventId(), principal);
        if (!Set.of("DRAFT", "SCHEDULED", "PUBLISHED", "IN_PROGRESS", "FINISHED")
                .contains(unit.getStatus()))
            throw new BusinessException(
                    "results.invalid_unit_status",
                    "Ввод результатов недоступен в текущем статусе заплыва");
        var unitEntries = entries.findByCompetitionUnitId(unitId);
        var entryIds = unitEntries.stream().map(e -> e.getId()).toList();
        var existing =
                results.findByCompetitionUnitEntryIdIn(entryIds).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        r -> r.getCompetitionUnitEntryId(), r -> r));
        boolean correcting =
                !existing.isEmpty() && Set.of("PUBLISHED", "FINISHED").contains(unit.getStatus());
        if (correcting && Set.of("PUBLISHED", "FINISHED").contains(unit.getStatus())) {
            var auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .getAuthentication();
            if (auth == null
                    || auth.getAuthorities().stream()
                            .noneMatch(
                                    a ->
                                            Set.of(
                                                            "ROLE_ORGANIZER",
                                                            "ROLE_OPERATOR",
                                                            "ROLE_PLATFORM_ADMIN")
                                                    .contains(a.getAuthority())))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Исправление опубликованных результатов недоступно судье");
        }
        var previous = json.valueToTree(details.execute(unitId));
        Set<UUID> seen = new HashSet<>();
        List<PreparedResult> prepared = new ArrayList<>();
        var errors =
                new ArrayList<com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation>();
        for (int i = 0; i < requests.size(); i++) {
            var r = requests.get(i);
            String prefix = "[" + i + "].";
            if (!entryIds.contains(r.competitionUnitEntryId()))
                errors.add(
                        new com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation(
                                prefix + "competitionUnitEntryId",
                                "Участник не принадлежит заплыву"));
            if (!seen.add(r.competitionUnitEntryId()))
                errors.add(
                        new com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation(
                                prefix + "competitionUnitEntryId", "Участник указан дважды"));
            Long time = r.timeMs();
            if (r.time() != null)
                try {
                    long parsed = TimeParser.parse(r.time());
                    if (time != null && time != parsed)
                        errors.add(
                                new com.acme.sportplatform.common.api.ApiErrorResponse
                                        .FieldViolation(
                                        prefix + "timeMs",
                                        "Время в строке и миллисекундах не совпадает"));
                    time = parsed;
                } catch (BusinessException ex) {
                    errors.add(
                            new com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation(
                                    prefix + "time", ex.getMessage()));
                }
            if ("VALID".equals(r.status()) && (time == null || time <= 0))
                errors.add(
                        new com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation(
                                prefix + "timeMs",
                                "Укажите положительное время для результата VALID"));
            String status =
                    switch (r.status()) {
                        case "VALID" -> "VALID";
                        case "DNS" -> "DID_NOT_START";
                        case "DNF" -> "DID_NOT_FINISH";
                        case "DSQ" -> "DISQUALIFIED";
                        default ->
                                throw com.acme.sportplatform.common.RequestValidation.field(
                                        "results.invalid_status",
                                        prefix + "status",
                                        "Недопустимый статус результата");
                    };
            prepared.add(
                    new PreparedResult(
                            r.competitionUnitEntryId(),
                            "VALID".equals(r.status()) ? time : null,
                            status));
        }
        com.acme.sportplatform.common.RequestValidation.requireValid(
                "results.invalid_request", errors);
        boolean changed = false;
        for (var p : prepared) {
            var result = existing.get(p.entryId());
            boolean rankedOld =
                    result != null && Set.of("VALID", "CORRECTED").contains(result.getStatus());
            boolean same =
                    result != null
                            && Objects.equals(result.getTimeMs(), p.timeMs())
                            && (result.getStatus().equals(p.status())
                                    || rankedOld
                                            && result.getTimeMs() != null
                                            && p.status().equals("VALID")
                                    || "CORRECTED".equals(result.getStatus())
                                            && result.getTimeMs() == null
                                            && Objects.equals(result.getRawValue(), p.status()));
            if (same) continue;
            changed = true;
            boolean correction = correcting && result != null;
            if (result == null) {
                result = new ResultEntity();
                result.setId(UUID.randomUUID());
                result.setCompetitionUnitEntryId(p.entryId());
                result.setCreatedAt(java.time.OffsetDateTime.now());
            }
            result.setTimeMs(p.timeMs());
            // Preserve the terminal outcome when CORRECTED replaces DNS/DNF/DSQ.
            result.setRawValue(
                    correction && p.timeMs() == null ? p.status() : TimeParser.format(p.timeMs()));
            result.setResultType("TIME");
            result.setRecordedByUserId(userId);
            result.setUpdatedAt(java.time.OffsetDateTime.now());
            result.setStatus(correction ? "CORRECTED" : p.status());
            results.save(result);
        }
        rankDiscipline(unit.getEventDisciplineId());
        var table = details.execute(unitId);
        if (correcting && changed) {
            UUID runId = logs.begin(discipline.eventId(), discipline.id(), "RANKING", previous, userId);
            logs.finish(runId, json.valueToTree(table), List.of(),
                    Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000));
        }
        return table;
    }

    private record PreparedResult(UUID entryId, Long timeMs, String status) {}

    public DisciplineResultsResponse protocol(
            UUID disciplineId,
            com.acme.sportplatform.identity.AuthenticatedUserPrincipal principal) {
        var discipline =
                disciplines
                        .findById(disciplineId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "competition.discipline_not_found",
                                                "Дисциплина не найдена"));
        access.assertCanManage(discipline.eventId(), principal);
        Map<String, List<CompetitionUnitDetailsResponse.EntryView>> groups = new TreeMap<>();
        for (var unit : units.findByEventDisciplineIdOrderBySequenceNumberAsc(disciplineId))
            for (var entry : details.execute(unit.getId()).entries()) {
                String group = unit.getAgeGroupCode();
                if (group == null || !"BY_AGE_GROUP".equals(discipline.seedingMode())) {
                    try {
                        var snapshot =
                                json.readTree(
                                        registrations
                                                .getById(entry.registrationId())
                                                .participantSnapshot());
                        int year =
                                java.time.LocalDate.parse(snapshot.path("birthDate").asText())
                                        .getYear();
                        String gender =
                                com.acme.sportplatform.identity.ProfileExternalKey.gender(
                                                snapshot.path("gender").asText())
                                        .toUpperCase(java.util.Locale.ROOT);
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
                groups.computeIfAbsent(group == null ? "UNGROUPED" : group, k -> new ArrayList<>())
                        .add(entry);
            }
        var comparator =
                Comparator.comparing(
                                CompetitionUnitDetailsResponse.EntryView::placeInAgeGroup,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                CompetitionUnitDetailsResponse.EntryView::timeMs,
                                Comparator.nullsLast(Comparator.naturalOrder()));
        return new DisciplineResultsResponse(
                disciplineId,
                groups.entrySet().stream()
                        .map(
                                g ->
                                        new DisciplineResultsResponse.AgeGroupResults(
                                                g.getKey(),
                                                g.getValue().stream().sorted(comparator).toList()))
                        .toList());
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
                                com.acme.sportplatform.identity.ProfileExternalKey.gender(
                                                snapshot.path("gender").asText())
                                        .toUpperCase(java.util.Locale.ROOT);
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
                        .filter(
                                r ->
                                        Set.of("VALID", "CORRECTED").contains(r.getStatus())
                                                && r.getTimeMs() != null)
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
