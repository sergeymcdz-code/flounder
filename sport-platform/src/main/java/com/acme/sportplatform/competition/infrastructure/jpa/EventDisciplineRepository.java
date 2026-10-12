package com.acme.sportplatform.competition.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventDisciplineRepository
        extends JpaRepository<EventDisciplineEntity, UUID> {

    List<EventDisciplineEntity> findByEventId(UUID eventId);

    List<EventDisciplineEntity> findByEventIdAndStatus(
            UUID eventId,
            String status
    );

    Optional<EventDisciplineEntity> findByEventIdAndCode(
            UUID eventId,
            String code
    );

    boolean existsByEventIdAndCode(UUID eventId, String code);
}