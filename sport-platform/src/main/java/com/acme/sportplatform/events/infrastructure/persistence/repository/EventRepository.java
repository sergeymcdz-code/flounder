package com.acme.sportplatform.events.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    List<EventEntity> findByOrganizationIdInOrCreatedByUserIdOrderByEventStartAtDesc(
            Collection<UUID> organizationIds, UUID userId);

    List<EventEntity> findAllByOrderByEventStartAtDesc();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from EventEntity e where e.id=:id")
    Optional<EventEntity> lockById(UUID id);

    boolean existsByPublicSlug(String publicSlug);

    List<EventEntity> findByStatusInOrderByEventStartAtAsc(
            Collection<String> statuses
    );

    Optional<EventEntity> findByPublicSlugAndStatusIn(
            String publicSlug,
            Collection<String> statuses
    );

    Optional<EventEntity> findByPublicSlug(String publicSlug);

    List<EventEntity> findByCreatedByUserIdOrderByEventStartAtDesc(UUID createdByUserId);
}