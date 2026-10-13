package com.acme.sportplatform.identity.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserRoleAssignmentEntity;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, UUID> {

    @Query("""
            select new com.acme.sportplatform.identity.api.UserDetailsResponse$Role(r.code, r.name, a.organizationId, a.eventId)
            from UserRoleAssignmentEntity a, RoleEntity r where a.roleId = r.id and a.userId = :userId
            order by r.code
            """)
    List<com.acme.sportplatform.identity.api.UserDetailsResponse.Role> findRolesByUserId(UUID userId);

    boolean existsByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(UUID userId, UUID roleId);

    void deleteByUserIdAndRoleIdAndEventIdIsNullAndOrganizationIdIsNull(UUID userId, UUID roleId);

    @Query("select r.code from UserRoleAssignmentEntity a, RoleEntity r where a.roleId = r.id and a.userId = :userId and a.organizationId is null and a.eventId is null")
    List<String> findGlobalRoleCodesByUserId(UUID userId);

    @Query("""
            select r.code
            from UserRoleAssignmentEntity ura, RoleEntity r
            where ura.roleId = r.id
              and ura.userId = :userId
            """)
    List<String> findRoleCodesByUserId(UUID userId);
}
