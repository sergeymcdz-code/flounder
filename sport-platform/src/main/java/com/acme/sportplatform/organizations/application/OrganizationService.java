package com.acme.sportplatform.organizations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.organizations.OrganizationLookup;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import com.acme.sportplatform.organizations.api.CreateOrganizationRequest;
import com.acme.sportplatform.organizations.api.OrganizationResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberRepository;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;

@Service
public class OrganizationService implements OrganizationLookup, OrganizationManagementAccess {

    private static final String OWNER = "OWNER";
    private static final String ADMIN = "ADMIN";

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(UUID organizationId) {
        return organizationRepository.existsById(organizationId);
    }

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        OrganizationEntity entity = new OrganizationEntity();
        entity.setType(request.type());
        entity.setName(request.name());
        entity.setLegalName(request.legalName());
        entity.setInn(request.inn());
        entity.setContactEmail(request.contactEmail());
        entity.setContactPhone(request.contactPhone());
        entity.setMeta("{}");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        OrganizationEntity saved = organizationRepository.save(entity);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(
            UUID organizationId,
            UUID currentUserId,
            boolean platformAdmin
    ) {
        OrganizationEntity organization;

        if (platformAdmin) {
            organization = organizationRepository.findById(organizationId)
                    .orElseThrow(this::organizationNotFound);
        } else {
            organization = organizationRepository.findByIdAndMemberUserId(
                            organizationId,
                            currentUserId
                    )
                    .orElseThrow(this::organizationNotFound);
        }

        return mapToResponse(organization);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getOrganizations(
            UUID currentUserId,
            boolean platformAdmin
    ) {
        List<OrganizationEntity> organizations;

        if (platformAdmin) {
            organizations = organizationRepository.findAllByOrderByNameAsc();
        } else {
            organizations = organizationRepository.findAllByMemberUserId(currentUserId);
        }

        return organizations.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canManageOrganization(UUID organizationId, UUID userId) {
        return organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .map(member ->
                        OWNER.equals(member.getMemberRole())
                                || ADMIN.equals(member.getMemberRole())
                )
                .orElse(false);
    }

    private BusinessException organizationNotFound() {
        return new BusinessException(
                "organizations.organization_not_found",
                "Organization not found"
        );
    }

    private OrganizationResponse mapToResponse(OrganizationEntity entity) {
        return new OrganizationResponse(
                entity.getId(),
                entity.getType(),
                entity.getName(),
                entity.getLegalName(),
                entity.getInn(),
                entity.getContactEmail(),
                entity.getContactPhone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}