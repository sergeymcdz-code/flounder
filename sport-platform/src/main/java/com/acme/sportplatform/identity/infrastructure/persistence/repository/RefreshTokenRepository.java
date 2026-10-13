package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.RefreshTokenEntity;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    @Query("select t.userId from RefreshTokenEntity t where t.tokenHash = :hash")
    Optional<UUID> findUserIdByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenEntity t where t.tokenHash = :hash")
    Optional<RefreshTokenEntity> findForUpdate(String hash);

    @Modifying
    @Query("update RefreshTokenEntity t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    void invalidateAll(UUID userId, Instant now);
}
