package com.acme.sportplatform.organizations.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
public interface OrganizationRegistrationRepository
        extends JpaRepository<OrganizationRegistrationEntity, UUID> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrganizationRegistrationEntity r where r.id = :id")
    Optional<OrganizationRegistrationEntity> findLockedById(UUID id);

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