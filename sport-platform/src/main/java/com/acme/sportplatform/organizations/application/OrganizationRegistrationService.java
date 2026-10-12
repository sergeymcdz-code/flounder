package com.acme.sportplatform.organizations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.organizations.api.CreateOrganizationRegistrationRequest;
import com.acme.sportplatform.organizations.api.OrganizationRegistrationResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRegistrationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRegistrationRepository;

@Service
public class OrganizationRegistrationService {

    private static final String PENDING_STATUS = "PENDING";

    private final OrganizationRegistrationRepository registrationRepository;

    public OrganizationRegistrationService(
            OrganizationRegistrationRepository registrationRepository
    ) {
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public OrganizationRegistrationResponse create(
            UUID applicantUserId,
            CreateOrganizationRegistrationRequest request
    ) {
        boolean pendingRequestExists =
                registrationRepository.existsByApplicantUserIdAndStatus(
                        applicantUserId,
                        PENDING_STATUS
                );

        if (pendingRequestExists) {
            throw new BusinessException(
                    "organizations.registration_already_pending",
                    "You already have a pending organization registration request"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        OrganizationRegistrationEntity entity =
                new OrganizationRegistrationEntity();

        entity.setApplicantUserId(applicantUserId);
        entity.setOrganizationType(request.organizationType());
        entity.setOrganizationName(request.organizationName());
        entity.setLegalName(request.legalName());
        entity.setInn(request.inn());
        entity.setContactEmail(request.contactEmail());
        entity.setContactPhone(request.contactPhone());
        entity.setStatus(PENDING_STATUS);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        OrganizationRegistrationEntity saved =
                registrationRepository.save(entity);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationRegistrationResponse getPendingForUser(
            UUID applicantUserId
    ) {
        OrganizationRegistrationEntity entity =
                registrationRepository
                        .findByApplicantUserIdAndStatus(
                                applicantUserId,
                                PENDING_STATUS
                        )
                        .orElseThrow(() -> new BusinessException(
                                "organizations.pending_registration_not_found",
                                "Pending organization registration was not found"
                        ));

        return mapToResponse(entity);
    }

    private OrganizationRegistrationResponse mapToResponse(
            OrganizationRegistrationEntity entity
    ) {
        return new OrganizationRegistrationResponse(
                entity.getId(),
                entity.getApplicantUserId(),
                entity.getOrganizationType(),
                entity.getOrganizationName(),
                entity.getLegalName(),
                entity.getInn(),
                entity.getContactEmail(),
                entity.getContactPhone(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getReviewedByUserId(),
                entity.getReviewedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}