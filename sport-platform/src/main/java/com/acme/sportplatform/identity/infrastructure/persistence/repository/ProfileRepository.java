package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {
    Optional<ProfileEntity> findByUserId(UUID userId);
}