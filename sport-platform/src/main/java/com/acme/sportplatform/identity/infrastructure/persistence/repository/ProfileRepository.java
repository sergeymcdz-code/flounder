package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;

public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {
    Optional<ProfileEntity> findByUserId(UUID userId);

    List<ProfileEntity> findAllByUserIdIn(Collection<UUID> userIds);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<ProfileEntity> findByExternalKey(String externalKey);
}
