package com.acme.sportplatform.organizations.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository
        extends JpaRepository<OrganizationMemberEntity, UUID> {

    boolean existsByOrganizationIdAndUserId(
            UUID organizationId,
            UUID userId
    );

    Optional<OrganizationMemberEntity> findByOrganizationIdAndUserId(
            UUID organizationId,
            UUID userId
    );

    List<OrganizationMemberEntity> findAllByUserId(
            UUID userId
    );
}