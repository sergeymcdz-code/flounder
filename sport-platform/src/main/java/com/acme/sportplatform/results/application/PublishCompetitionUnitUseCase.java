package com.acme.sportplatform.results.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.domain.ResultStatus;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;

@Service
public class PublishCompetitionUnitUseCase {

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final ResultRepository resultRepository;

    public PublishCompetitionUnitUseCase(
            CompetitionUnitRepository competitionUnitRepository,
            CompetitionUnitEntryRepository entryRepository,
            ResultRepository resultRepository
    ) {
        this.competitionUnitRepository = competitionUnitRepository;
        this.entryRepository = entryRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional
    public void execute(UUID unitId, boolean published) {
        CompetitionUnitEntity unit = competitionUnitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessException(
                        "results.unit_not_found",
                        "Competition unit not found: " + unitId
                ));

        if (!published) {
            if ("DRAFT".equals(unit.getStatus())) {
                throw new BusinessException(
                        "results.unit_already_draft",
                        "Competition unit is already a draft"
                );
            }

            unit.setStatus("DRAFT");
            return;
        }

        if ("PUBLISHED".equals(unit.getStatus())) {
            throw new BusinessException(
                    "results.unit_already_published",
                    "Competition unit is already published"
            );
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

        unit.setStatus("PUBLISHED");
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

        if (ResultStatus.VALID.name().equals(status)) {
            if (result.getFinalPlace() == null) {
                throw new BusinessException(
                        "results.unit_has_unranked_results",
                        "Нельзя опубликовать: у одного или нескольких участников не рассчитано место."
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