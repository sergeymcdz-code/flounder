package com.acme.sportplatform.organizations.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.UserRoleLookup;
import com.acme.sportplatform.organizations.OrganizationLookup;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import com.acme.sportplatform.organizations.api.AddOrganizationMemberRequest;
import com.acme.sportplatform.organizations.api.CreateOrganizationRequest;
import com.acme.sportplatform.organizations.api.OrganizationMemberResponse;
import com.acme.sportplatform.organizations.api.OrganizationResponse;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberRepository;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService implements OrganizationLookup, OrganizationManagementAccess {

    private final UserRoleLookup userRoleLookup;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            UserRoleLookup userRoleLookup
    ) {
        this.organizationRepository = organizationRepository;
        this.userRoleLookup = userRoleLookup;
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
        return isMember(organizationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(UUID organizationId, UUID userId) {
        return organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, userId);
    }

    private void assertOwner(UUID organizationId, UUID userId, boolean admin) {
        if (!organizationRepository.existsById(organizationId)) throw organizationNotFound();
        if (!admin && !organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(m -> "owner".equalsIgnoreCase(m.getMemberRole())).orElse(false)) {
            throw new BusinessException("organizations.access_denied", "Только владелец организации может управлять участниками");
        }
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> members(
            UUID organizationId, UUID userId, boolean admin) {
        assertOwner(organizationId, userId, admin);
        return organizationMemberRepository.findAllByOrganizationId(organizationId).stream()
                .map(this::memberResponse).toList();
    }

    @Transactional
    public OrganizationMemberResponse addMember(
            UUID organizationId, AddOrganizationMemberRequest request,
            UUID userId, boolean admin) {
        assertOwner(organizationId, userId, admin);
        return memberResponse(putMember(organizationId, request.userId(), request.memberRole()));
    }

    @Transactional
    public OrganizationMemberEntity putMember(
            UUID organizationId, UUID userId, String role) {
        userRoleLookup.requireUser(userId);
        String normalized = role.toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("owner", "admin", "member", "organizer", "operator", "judge").contains(normalized)) {
            throw new BusinessException("organizations.invalid_member_role", "Неизвестная роль участника организации");
        }
        var member = organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseGet(OrganizationMemberEntity::new);
        member.setOrganizationId(organizationId);
        member.setUserId(userId);
        member.setMemberRole(normalized);
        if (member.getCreatedAt() == null) member.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return organizationMemberRepository.save(member);
    }

    private OrganizationMemberResponse memberResponse(
            OrganizationMemberEntity m) {
        return new OrganizationMemberResponse(
                m.getId(), m.getOrganizationId(), m.getUserId(), m.getMemberRole(), m.getCreatedAt());
    }

    private BusinessException organizationNotFound() {
        return new BusinessException(
                "organizations.organization_not_found",
                "Организация не найдена"
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