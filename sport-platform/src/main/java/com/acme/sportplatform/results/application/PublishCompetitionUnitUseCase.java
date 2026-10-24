package com.acme.sportplatform.results.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.domain.CompetitionUnitStatus;
import com.acme.sportplatform.results.domain.ResultStatus;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PublishCompetitionUnitUseCase {

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final ResultRepository resultRepository;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public PublishCompetitionUnitUseCase(
            CompetitionUnitRepository competitionUnitRepository,
            CompetitionUnitEntryRepository entryRepository,
            ResultRepository resultRepository,
            org.springframework.context.ApplicationEventPublisher publisher
    ) {
        this.competitionUnitRepository = competitionUnitRepository;
        this.entryRepository = entryRepository;
        this.resultRepository = resultRepository;
        this.publisher = publisher;
    }

    @Transactional
    public void execute(UUID unitId, boolean published) {
        execute(unitId, published, null);
    }

    @Transactional
    public void execute(UUID unitId, boolean published, Boolean official) {
        if (!published && Boolean.TRUE.equals(official)) {
            throw new BusinessException("results.official_requires_publication",
                    "Официальный протокол должен быть опубликован");
        }
        CompetitionUnitEntity unit =
                competitionUnitRepository
                        .lockById(unitId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                "results.unit_not_found",
                                                "Заплыв не найден: " + unitId));

        if (!published) {
            if (CompetitionUnitStatus.COMPLETED.name().equals(unit.getStatus())) {
                throw new BusinessException(
                        "results.invalid_transition", "Нельзя снять результаты завершённого соревнования с публикации");
            }
            if (!CompetitionUnitStatus.FINISHED.name().equals(unit.getStatus())) {
                throw new BusinessException(
                        "results.unit_not_published", "Результаты заплыва не опубликованы");
            }

            unit.setOfficial(false);
            unit.setStatus(CompetitionUnitStatus.PUBLISHED.name());
            unit.setUpdatedAt(OffsetDateTime.now());
            return;
        }

        boolean alreadyPublished = List.of("FINISHED", "COMPLETED").contains(unit.getStatus());
        switch (CompetitionUnitStatus.valueOf(unit.getStatus())) {
            case FINISHED, COMPLETED -> {
                if (official == null) throw new BusinessException(
                        "results.unit_already_published", "Результаты заплыва уже опубликованы");
            }
            case DRAFT, SCHEDULED -> throw new BusinessException(
                    "results.start_list_not_published", "Сначала опубликуйте стартовый протокол");
            case PUBLISHED, IN_PROGRESS -> { }
            default -> throw new BusinessException(
                    "results.invalid_transition", "Нельзя опубликовать результаты из текущего статуса заплыва");
        }

        List<CompetitionUnitEntryEntity> entries =
                entryRepository.findByCompetitionUnitId(unitId);

        if (entries.isEmpty()) {
            throw new BusinessException(
                    "results.unit_has_no_entries",
                    "Нельзя опубликовать заплыв без участников."
            );
        }

        List<UUID> entryIds = entries.stream()
                .map(CompetitionUnitEntryEntity::getId)
                .toList();

        List<ResultEntity> results =
                resultRepository.findByCompetitionUnitEntryIdIn(entryIds);

        Map<UUID, ResultEntity> resultByEntryId = results.stream()
                .collect(Collectors.toMap(
                        ResultEntity::getCompetitionUnitEntryId,
                        Function.identity()
                ));

        for (CompetitionUnitEntryEntity entry : entries) {
            ResultEntity result = resultByEntryId.get(entry.getId());

            validateResultForPublication(result);
        }

        unit.setOfficial(Boolean.TRUE.equals(official));
        if (alreadyPublished) {
            unit.setUpdatedAt(OffsetDateTime.now());
            return;
        }
        unit.setStatus(CompetitionUnitStatus.FINISHED.name());
        var now = OffsetDateTime.now();
        if (unit.getActualFinishedAt() == null) unit.setActualFinishedAt(now);
        unit.setUpdatedAt(now);
        publisher.publishEvent(new com.acme.sportplatform.results.ResultsPublishedEvent(
                unitId, unit.getEventDisciplineId(), entries.stream()
                        .map(CompetitionUnitEntryEntity::getRegistrationId).toList()));
    }

    private void validateResultForPublication(ResultEntity result) {
        if (result == null) {
            throw new BusinessException(
                    "results.unit_has_missing_results",
                    "Нельзя опубликовать: у одного или нескольких участников нет результата."
            );
        }

        String status = result.getStatus();

        if (ResultStatus.PENDING.name().equals(status)) {
            throw new BusinessException(
                    "results.unit_has_unvalidated_results",
                    "Нельзя опубликовать: сначала пересчитайте результаты."
            );
        }

        if (ResultStatus.CORRECTED.name().equals(status) && result.getTimeMs() == null
                && java.util.Set.of("DID_NOT_START", "DID_NOT_FINISH", "DISQUALIFIED")
                        .contains(result.getRawValue() == null ? "" : result.getRawValue())) {
            return;
        }

        if (ResultStatus.VALID.name().equals(status)
                || ResultStatus.CORRECTED.name().equals(status)) {
            if (result.getFinalPlace() == null) {
                throw new BusinessException(
                        "results.unit_has_unranked_results",
                        "Нельзя опубликовать: у одного или нескольких участников не рассчитано"
                            + " место."
                );
            }

            return;
        }

        if (ResultStatus.DID_NOT_START.name().equals(status)
                || ResultStatus.DID_NOT_FINISH.name().equals(status)
                || ResultStatus.DISQUALIFIED.name().equals(status)) {
            return;
        }

        throw new BusinessException(
                "results.invalid_status",
                "Нельзя опубликовать: найден недопустимый статус результата."
        );
    }
}
