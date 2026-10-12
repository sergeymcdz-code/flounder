package com.acme.sportplatform.results.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultRepository extends JpaRepository<ResultEntity, UUID> {
    Optional<ResultEntity> findByCompetitionUnitEntryId(UUID competitionUnitEntryId);
    List<ResultEntity> findByCompetitionUnitEntryIdIn(
        List<UUID> competitionUnitEntryIds);
}