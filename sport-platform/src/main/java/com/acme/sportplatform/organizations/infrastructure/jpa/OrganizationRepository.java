package com.acme.sportplatform.organizations.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository
        extends JpaRepository<OrganizationEntity, UUID> {

    List<OrganizationEntity> findAllByOrderByNameAsc();

    @Query("""
            select organization
            from OrganizationEntity organization
            where exists (
                select member.id
                from OrganizationMemberEntity member
                where member.organizationId = organization.id
                  and member.userId = :userId
            )
            order by organization.name asc
            """)
    List<OrganizationEntity> findAllByMemberUserId(
            @Param("userId") UUID userId
    );

    @Query("""
            select organization
            from OrganizationEntity organization
            where organization.id = :organizationId
              and exists (
                  select member.id
                  from OrganizationMemberEntity member
                  where member.organizationId = organization.id
                    and member.userId = :userId
              )
            """)
    Optional<OrganizationEntity> findByIdAndMemberUserId(
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId
    );
}