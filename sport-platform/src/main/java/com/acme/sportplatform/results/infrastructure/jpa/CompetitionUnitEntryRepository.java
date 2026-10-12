package com.acme.sportplatform.results.infrastructure.jpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionUnitEntryRepository
        extends JpaRepository<CompetitionUnitEntryEntity, UUID> {

        List<CompetitionUnitEntryEntity> findByCompetitionUnitId(
                UUID competitionUnitId
        );

        List<CompetitionUnitEntryEntity> findByCompetitionUnitIdIn(
                List<UUID> competitionUnitIds
        );

        boolean existsByCompetitionUnitIdAndRegistrationId(
                UUID competitionUnitId,
                UUID registrationId
        );

        @Query("""
                select case when count(entry) > 0 then true else false end
                from CompetitionUnitEntryEntity entry
                where entry.competitionUnitId = :competitionUnitId
                and entry.laneOrPosition = :laneOrPosition
                """)
        boolean existsByCompetitionUnitIdAndLaneOrPosition(
                @Param("competitionUnitId") UUID competitionUnitId,
                @Param("laneOrPosition") Integer laneOrPosition
        );

}