package com.acme.sportplatform.competition.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventDisciplineRepository
        extends JpaRepository<EventDisciplineEntity, UUID> {
    boolean existsBySessionId(UUID id);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select d from EventDisciplineEntity d where d.id=:id")
    Optional<EventDisciplineEntity> lockById(UUID id);

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