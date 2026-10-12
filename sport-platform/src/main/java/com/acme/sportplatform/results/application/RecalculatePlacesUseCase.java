package com.acme.sportplatform.results.application;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
public class RecalculatePlacesUseCase {

    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;
    private final ResultRepository resultRepository;

    public RecalculatePlacesUseCase(
            CompetitionUnitRepository competitionUnitRepository,
            CompetitionUnitEntryRepository entryRepository,
            ResultRepository resultRepository
    ) {
        this.competitionUnitRepository = competitionUnitRepository;
        this.entryRepository = entryRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional
    public void execute(
            UUID competitionUnitId,
            String rankingStrategy
    ) {
        boolean descending = parseRankingStrategy(rankingStrategy);

        CompetitionUnitEntity unit = competitionUnitRepository
                .findById(competitionUnitId)
                .orElseThrow(() -> new BusinessException(
                        "results.unit_not_found",
                        "Competition unit not found: " + competitionUnitId
                ));

        if ("PUBLISHED".equals(unit.getStatus())) {
            throw new BusinessException(
                    "results.unit_published",
                    "Нельзя пересчитать места: заплыв опубликован. Сначала снимите его с публикации."
            );
        }

        List<CompetitionUnitEntryEntity> entries =
                entryRepository.findByCompetitionUnitId(competitionUnitId);

        List<UUID> entryIds = entries.stream()
                .map(CompetitionUnitEntryEntity::getId)
                .toList();

        List<ResultEntity> allResults = entryIds.isEmpty()
                ? List.of()
                : resultRepository.findByCompetitionUnitEntryIdIn(entryIds);

        boolean hasInvalidPendingResult = allResults.stream()
                .filter(result ->
                        ResultStatus.PENDING.name().equals(result.getStatus()))
                .anyMatch(result ->
                        parseNumeric(result.getRawValue()) == null);

        if (hasInvalidPendingResult) {
            throw new BusinessException(
                    "results.invalid_raw_value",
                    "Нельзя пересчитать места: один или несколько результатов имеют неверный формат."
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        for (ResultEntity result : allResults) {
            if (canBeRanked(result)) {
                result.setFinalPlace(null);
                result.setUpdatedAt(now);
            }
        }

        List<RankedResult> rankedResults = allResults.stream()
                .filter(this::canBeRanked)
                .map(result -> new RankedResult(
                        result,
                        parseNumeric(result.getRawValue())
                ))
                .sorted(rankedComparator(descending))
                .toList();

        int place = 1;

        for (RankedResult ranked : rankedResults) {
            ResultEntity result = ranked.result();

            result.setFinalPlace(place++);
            result.setStatus(ResultStatus.VALID.name());
            result.setUpdatedAt(now);
        }

        resultRepository.saveAll(allResults);
    }

    private boolean parseRankingStrategy(String rankingStrategy) {
        if ("ASC".equalsIgnoreCase(rankingStrategy)) {
            return false;
        }

        if ("DESC".equalsIgnoreCase(rankingStrategy)) {
            return true;
        }

        throw new BusinessException(
                "results.invalid_ranking_strategy",
                "rankingStrategy must be ASC or DESC"
        );
    }

    private boolean canBeRanked(ResultEntity result) {
        return ResultStatus.PENDING.name().equals(result.getStatus())
                || ResultStatus.VALID.name().equals(result.getStatus());
    }

    private Comparator<RankedResult> rankedComparator(boolean descending) {
        Comparator<RankedResult> comparator =
                Comparator.comparing(RankedResult::value);

        return descending ? comparator.reversed() : comparator;
    }

    private Double parseNumeric(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            if (!rawValue.contains(":")) {
                return Double.parseDouble(rawValue);
            }

            String[] parts = rawValue.split(":");

            if (parts.length != 2) {
                return null;
            }

            double minutes = Double.parseDouble(parts[0]);
            double seconds = Double.parseDouble(parts[1]);

            if (minutes < 0 || seconds < 0 || seconds >= 60) {
                return null;
            }

            return minutes * 60 + seconds;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record RankedResult(
            ResultEntity result,
            Double value
    ) {
    }
}