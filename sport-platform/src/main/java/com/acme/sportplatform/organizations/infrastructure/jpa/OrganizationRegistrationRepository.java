package com.acme.sportplatform.organizations.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRegistrationRepository
        extends JpaRepository<OrganizationRegistrationEntity, UUID> {

    boolean existsByApplicantUserIdAndStatus(
            UUID applicantUserId,
            String status
    );

    Optional<OrganizationRegistrationEntity>
            findByApplicantUserIdAndStatus(
                    UUID applicantUserId,
                    String status
            );

    List<OrganizationRegistrationEntity>
            findByStatusOrderByCreatedAtAsc(
                    String status
            );

    List<OrganizationRegistrationEntity>
            findByApplicantUserIdOrderByCreatedAtDesc(
                    UUID applicantUserId
            );
}