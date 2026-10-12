package com.acme.sportplatform.results.infrastructure.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionUnitRepository
        extends JpaRepository<CompetitionUnitEntity, UUID> {
    boolean existsByEventDisciplineId(UUID id);

    List<CompetitionUnitEntity>
    findByEventDisciplineIdOrderBySequenceNumberAsc(
            UUID eventDisciplineId
    );

    List<CompetitionUnitEntity>
    findByEventDisciplineIdAndStatusOrderBySequenceNumberAsc(
            UUID eventDisciplineId,
            String status
    );
}