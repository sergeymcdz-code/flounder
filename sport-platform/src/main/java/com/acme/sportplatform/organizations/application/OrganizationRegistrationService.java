package com.acme.sportplatform.organizations.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.UserRoleLookup;
import com.acme.sportplatform.organizations.api.CreateOrganizationRegistrationRequest;
import com.acme.sportplatform.organizations.api.CreateOrganizationRequest;
import com.acme.sportplatform.organizations.api.OrganizationRegistrationResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRegistrationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRegistrationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationRegistrationService {

    private static final String PENDING_STATUS = "PENDING";

    private final OrganizationRegistrationRepository registrationRepository;
    private final OrganizationService organizationService;
    private final UserRoleLookup userRoleLookup;

    public OrganizationRegistrationService(
            OrganizationRegistrationRepository registrationRepository,
            OrganizationService organizationService,
            UserRoleLookup userRoleLookup
    ) {
        this.registrationRepository = registrationRepository;
        this.organizationService = organizationService;
        this.userRoleLookup = userRoleLookup;
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
                    "У вас уже есть заявка организации на рассмотрении"
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
                                "Заявка организации на рассмотрении не найдена"
                        ));

        return mapToResponse(entity);
    }

    @Transactional
    public OrganizationRegistrationResponse approve(UUID id, UUID reviewer, UUID organizationId) {
        var entity = pending(id);
        if (organizationId == null) {
            organizationId = organizationService.createOrganization(new CreateOrganizationRequest(
                    entity.getOrganizationType(), entity.getOrganizationName(), entity.getLegalName(), entity.getInn(),
                    entity.getContactEmail(), entity.getContactPhone())).id();
        } else if (!organizationService.existsById(organizationId)) {
            throw new BusinessException("organizations.organization_not_found", "Организация не найдена");
        }
        userRoleLookup.ensureOrganizer(entity.getApplicantUserId());
        organizationService.putMember(organizationId, entity.getApplicantUserId(), "owner");
        reviewed(entity, reviewer, "APPROVED");
        return mapToResponse(entity);
    }

    @Transactional
    public OrganizationRegistrationResponse reject(UUID id, UUID reviewer, String comment) {
        var entity = pending(id);
        entity.setRejectionReason(comment);
        reviewed(entity, reviewer, "REJECTED");
        return mapToResponse(entity);
    }

    private OrganizationRegistrationEntity pending(UUID id) {
        var entity = registrationRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException("organizations.registration_not_found", "Заявка не найдена"));
        if (!PENDING_STATUS.equals(entity.getStatus())) {
            throw new BusinessException("organizations.registration_already_reviewed", "Заявка уже рассмотрена");
        }
        return entity;
    }

    private void reviewed(OrganizationRegistrationEntity entity, UUID reviewer, String status) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setStatus(status);
        entity.setReviewedByUserId(reviewer);
        entity.setReviewedAt(now);
        entity.setUpdatedAt(now);
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