package com.acme.sportplatform.events.infrastructure.persistence.repository;

import com.acme.sportplatform.events.infrastructure.persistence.entity.EventSessionEntity;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface EventSessionRepository extends JpaRepository<EventSessionEntity, UUID> {
    List<EventSessionEntity> findByEventIdOrderByOrderNoAsc(UUID eventId);

    boolean existsByEventIdAndOrderNoAndIdNot(UUID eventId, int orderNo, UUID id);
}
