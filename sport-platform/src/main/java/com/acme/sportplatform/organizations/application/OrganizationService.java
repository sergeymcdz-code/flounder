package com.acme.sportplatform.organizations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.identity.UserMembershipLookup;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Service
public class OrganizationService implements OrganizationLookup, OrganizationManagementAccess, UserMembershipLookup {

    private final ObjectMapper objectMapper;
    private final UserRoleLookup userRoleLookup;
    private final UserLookup userLookup;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            UserRoleLookup userRoleLookup, UserLookup userLookup, ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.organizationRepository = organizationRepository;
        this.userRoleLookup = userRoleLookup;
        this.userLookup = userLookup;
        this.organizationMemberRepository = organizationMemberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public String nameById(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .map(OrganizationEntity::getName).orElseThrow(this::organizationNotFound);
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
        var meta = objectMapper.createObjectNode();
        if (request.city() != null) meta.put("city", request.city());
        entity.setMeta(meta.toString());
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

        if (organizations.isEmpty()) return List.of();
        var organizationIds = organizations.stream().map(OrganizationEntity::getId).toList();
        var members = organizationMemberRepository.findAllByOrganizationIdIn(organizationIds);
        var membersByOrganization = members.stream().collect(groupingBy(OrganizationMemberEntity::getOrganizationId));
        var ownersById = userMap(members.stream().filter(m -> "owner".equalsIgnoreCase(m.getMemberRole()))
                .map(OrganizationMemberEntity::getUserId).distinct().toList());
        return organizations.stream()
                .map(organization -> mapToResponse(organization, membersByOrganization.getOrDefault(organization.getId(), List.of()), ownersById))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canManageOrganization(UUID organizationId, UUID userId) {
        return organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(member -> Set.of("owner", "admin", "organizer")
                        .contains(member.getMemberRole().toLowerCase(Locale.ROOT)))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOwnerOrAdmin(UUID organizationId, UUID userId) {
        return organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(member -> Set.of("owner", "admin")
                        .contains(member.getMemberRole().toLowerCase(Locale.ROOT)))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> organizationIdsOf(UUID userId) {
        return organizationMemberRepository.findAllByUserId(userId).stream()
                .map(OrganizationMemberEntity::getOrganizationId).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(UUID organizationId, UUID userId) {
        return organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, userId);
    }

    private void assertOwner(UUID organizationId, UUID userId, boolean admin) {
        if (!organizationRepository.existsById(organizationId)) throw organizationNotFound();
        if (!admin && !organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .map(m -> Set.of("owner", "admin").contains(m.getMemberRole().toLowerCase(Locale.ROOT))).orElse(false)) {
            throw new BusinessException("organizations.access_denied", "Только владелец или администратор организации может управлять участниками");
        }
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> members(
            UUID organizationId, UUID userId, boolean admin) {
        assertOwner(organizationId, userId, admin);
        var members = organizationMemberRepository.findAllByOrganizationId(organizationId);
        var usersById = userMap(members.stream().map(OrganizationMemberEntity::getUserId).distinct().toList());
        return members.stream().map(member -> memberResponse(member, usersById.get(member.getUserId()))).toList();
    }

    @Transactional
    public OrganizationMemberResponse addMember(
            UUID organizationId, AddOrganizationMemberRequest request,
            UUID userId, boolean admin) {
        assertOwner(organizationId, userId, admin);
        if ((request.userId() == null) == (request.email() == null || request.email().isBlank()))
            throw new BusinessException("organizations.invalid_member_user", "Укажите email или userId пользователя, только одно поле");
        UUID memberUserId = request.userId() != null ? request.userId() : userLookup.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("organizations.user_not_found", "Пользователь с таким email не найден")).id();
        return memberResponse(putMember(organizationId, memberUserId, request.memberRole()));
    }

    @Transactional
    public OrganizationMemberEntity putMember(
            UUID organizationId, UUID userId, String role) {
        userRoleLookup.requireUser(userId);
        String normalized = role.toLowerCase(Locale.ROOT);
        if (!Set.of("owner", "admin", "member", "organizer", "operator", "judge").contains(normalized)) {
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

    @Override
    @Transactional(readOnly = true)
    public List<Membership> membershipsOf(UUID userId) {
        var members = organizationMemberRepository.findAllByUserId(userId);
        if (members.isEmpty()) return List.of();
        var organizationsById = organizationRepository.findAllById(members.stream()
                .map(OrganizationMemberEntity::getOrganizationId).distinct().toList()).stream()
                .collect(toMap(OrganizationEntity::getId, organization -> organization));
        return members.stream().map(member -> {
            var organization = organizationsById.get(member.getOrganizationId());
            if (organization == null) throw organizationNotFound();
            return new Membership(member.getOrganizationId(), organization.getName(), member.getMemberRole());
        }).toList();
    }

    @Transactional
    public OrganizationMemberResponse updateMember(UUID organizationId, UUID targetUserId, String role, UUID actor, boolean admin) {
        assertOwner(organizationId, actor, admin);
        requireMember(organizationId, targetUserId);
        return memberResponse(putMember(organizationId, targetUserId, role));
    }

    @Transactional
    public void deleteMember(UUID organizationId, UUID targetUserId, UUID actor, boolean admin) {
        assertOwner(organizationId, actor, admin);
        organizationMemberRepository.delete(requireMember(organizationId, targetUserId));
    }

    private OrganizationMemberEntity requireMember(UUID organizationId, UUID userId) {
        return organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new BusinessException("organizations.member_not_found", "Участник организации не найден"));
    }

    private OrganizationMemberResponse memberResponse(
            OrganizationMemberEntity m) {
        var user = userLookup.findById(m.getUserId()).orElseThrow(() -> new BusinessException("organizations.user_not_found", "Пользователь не найден"));
        return memberResponse(m, user);
    }

    private OrganizationMemberResponse memberResponse(OrganizationMemberEntity m, UserLookup.User user) {
        if (user == null) throw new BusinessException("organizations.user_not_found", "Пользователь не найден");
        return new OrganizationMemberResponse(
                m.getId(), m.getOrganizationId(), m.getUserId(), m.getMemberRole(), m.getCreatedAt(),
                user.email(), user.firstName(), user.lastName());
    }

    private BusinessException organizationNotFound() {
        return new BusinessException(
                "organizations.organization_not_found",
                "Организация не найдена"
        );
    }

    private Map<UUID, UserLookup.User> userMap(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return userLookup.findAllByIds(ids).stream().collect(toMap(UserLookup.User::id, user -> user));
    }

    private OrganizationResponse mapToResponse(OrganizationEntity entity) {
        var members = organizationMemberRepository.findAllByOrganizationId(entity.getId());
        var ownersById = userMap(members.stream().filter(m -> "owner".equalsIgnoreCase(m.getMemberRole()))
                .map(OrganizationMemberEntity::getUserId).distinct().toList());
        return mapToResponse(entity, members, ownersById);
    }

    private OrganizationResponse mapToResponse(OrganizationEntity entity, List<OrganizationMemberEntity> members,
                                               Map<UUID, UserLookup.User> ownersById) {
        String city = null;
        try { city = objectMapper.readTree(entity.getMeta()).path("city").asText(null); }
        catch (JsonProcessingException ignored) { }
        String owner = members.stream().filter(m -> "owner".equalsIgnoreCase(m.getMemberRole()))
                .map(m -> ownersById.containsKey(m.getUserId()) ? ownersById.get(m.getUserId()).email() : "—")
                .collect(java.util.stream.Collectors.joining(", "));

        return new OrganizationResponse(
                entity.getId(),
                entity.getType(),
                entity.getName(),
                entity.getLegalName(),
                entity.getInn(),
                entity.getContactEmail(),
                entity.getContactPhone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(), city, owner, members.size()
        );
    }
}
