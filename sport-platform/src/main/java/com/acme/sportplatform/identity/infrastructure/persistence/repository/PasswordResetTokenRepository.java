package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.PasswordResetTokenEntity;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
    @Query("select t.userId from PasswordResetTokenEntity t where t.tokenHash = :hash")
    Optional<UUID> findUserIdByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetTokenEntity t where t.tokenHash = :hash")
    Optional<PasswordResetTokenEntity> findForUpdate(String hash);

    @Modifying
    @Query("update PasswordResetTokenEntity t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    void invalidateAll(UUID userId, Instant now);
}
