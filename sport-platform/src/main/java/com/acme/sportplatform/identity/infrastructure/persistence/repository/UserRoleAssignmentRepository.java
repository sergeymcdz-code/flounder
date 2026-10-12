package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserRoleAssignmentEntity;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, UUID> {

    boolean existsByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(UUID userId, UUID roleId);

    void deleteByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(UUID userId, UUID roleId);

    @Query("""
            select r.code
            from UserRoleAssignmentEntity ura, RoleEntity r
            where ura.roleId = r.id
              and ura.userId = :userId
            """)
    List<String> findRoleCodesByUserId(UUID userId);
}