package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.results.api.RecordResultRequest;
import com.acme.sportplatform.results.api.ResultResponse;
import com.acme.sportplatform.results.domain.ResultStatus;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;

@Service
public class RecordResultUseCase {

    private final ResultRepository resultRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final CompetitionUnitRepository competitionUnitRepository;

    public RecordResultUseCase(
            ResultRepository resultRepository,
            CompetitionUnitEntryRepository entryRepository,
            CompetitionUnitRepository competitionUnitRepository
    ) {
        this.resultRepository = resultRepository;
        this.entryRepository = entryRepository;
        this.competitionUnitRepository = competitionUnitRepository;
    }

    @Transactional
    public ResultResponse execute(
            RecordResultRequest request,
            String resultType,
            UUID recordedByUserId
    ) {
        return execute(
                request,
                resultType,
                recordedByUserId,
                null
        );
    }

    @Transactional
    public ResultResponse execute(
            RecordResultRequest request,
            String resultType,
            UUID recordedByUserId,
            UUID expectedCompetitionUnitId
    ) {
        CompetitionUnitEntryEntity entry = entryRepository
                .findById(request.competitionUnitEntryId())
                .orElseThrow(() -> new BusinessException(
                        "results.entry_not_found",
                        "Участник заплыва не найден"
                ));
        if (expectedCompetitionUnitId != null
                && !expectedCompetitionUnitId.equals(entry.getCompetitionUnitId())) {
            throw new BusinessException(
                    "results.entry_unit_mismatch",
                    "Участник не принадлежит указанному заплыву или матчу."
            );
        }

        CompetitionUnitEntity unit = competitionUnitRepository
                .findById(entry.getCompetitionUnitId())
                .orElseThrow(() -> new BusinessException(
                        "results.unit_not_found",
                        "Competition unit not found: " + entry.getCompetitionUnitId()
                ));

        if ("PUBLISHED".equals(unit.getStatus())) {
            throw new BusinessException(
                    "results.unit_published",
                    "Нельзя изменить результат: заплыв опубликован. Сначала снимите его с публикации."
            );
        }

        ResultStatus requestedStatus = parseRequestedStatus(request.status());

        if (requestedStatus == ResultStatus.VALID) {
            throw new BusinessException(
                    "results.status_not_allowed",
                    "Статус VALID назначается только после пересчёта мест."
            );
        }

        if (requestedStatus == ResultStatus.PENDING
                && (request.rawValue() == null || request.rawValue().isBlank())) {
            throw new BusinessException(
                    "results.raw_value_required",
                    "Для обычного результата укажите время или количество очков."
            );
        }

        ResultEntity result = resultRepository
                .findByCompetitionUnitEntryId(entry.getId())
                .orElseGet(ResultEntity::new);

        if (result.getId() == null) {
            result.setId(UUID.randomUUID());
            result.setCreatedAt(OffsetDateTime.now());
        }

        result.setCompetitionUnitEntryId(entry.getId());
        result.setResultType(resultType);
        result.setRecordedByUserId(recordedByUserId);
        result.setUpdatedAt(OffsetDateTime.now());
        result.setFinalPlace(null);

        if (requestedStatus == ResultStatus.PENDING) {
            result.setRawValue(request.rawValue().trim());
            result.setStatus(ResultStatus.PENDING.name());
        } else {
            result.setRawValue(null);
            result.setStatus(requestedStatus.name());
        }

        ResultEntity saved = resultRepository.save(result);

        return toResponse(saved);
    }

    private ResultStatus parseRequestedStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return ResultStatus.PENDING;
        }

        try {
            ResultStatus status = ResultStatus.valueOf(
                    rawStatus.trim().toUpperCase(Locale.ROOT)
            );

            if (status == ResultStatus.PENDING
                    || status == ResultStatus.DID_NOT_START
                    || status == ResultStatus.DID_NOT_FINISH
                    || status == ResultStatus.DISQUALIFIED
                    || status == ResultStatus.VALID) {
                return status;
            }
        } catch (IllegalArgumentException exception) {
            // Ниже будет единая бизнес-ошибка.
        }

        throw new BusinessException(
                "results.invalid_status",
                "Допустимые статусы результата: PENDING, DNS, DNF, DSQ."
        );
    }

    private ResultResponse toResponse(ResultEntity entity) {
        return new ResultResponse(
                entity.getId(),
                entity.getCompetitionUnitEntryId(),
                entity.getRawValue(),
                entity.getResultType(),
                entity.getStatus(),
                entity.getFinalPlace()
        );
    }
}