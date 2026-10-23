package com.acme.sportplatform.organizations.infrastructure.jpa;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository
        extends JpaRepository<OrganizationMemberEntity, UUID> {

    @org.springframework.data.jpa.repository.Query(value = """
            select u.email, u.status, u.tokens_valid_after as "tokensValidAfter", r.code as "roleCode", m.organization_id as "organizationId",
                   o.name as "organizationName", lower(m.member_role) as "memberRole"
            from users u
            left join user_role_assignments a on a.user_id=u.id and a.organization_id is null and a.event_id is null
            left join roles r on r.id=a.role_id
            left join organization_members m on m.user_id=u.id
            left join organizations o on o.id=m.organization_id
            where u.id=:userId
            """, nativeQuery = true)
    List<com.acme.sportplatform.identity.UserMembershipLookup.AuthenticationRow> authenticationOf(UUID userId);

    List<OrganizationMemberEntity> findAllByOrganizationId(UUID organizationId);

    List<OrganizationMemberEntity> findAllByOrganizationIdIn(Collection<UUID> organizationIds);

    @org.springframework.data.jpa.repository.Query(value = """
            select o.id, o.name, o.meta->>'city' as city, lower(m.member_role) as "memberRole",
                   (select count(*) from events e where e.organization_id=o.id) as "eventsCount"
            from organization_members m join organizations o on o.id=m.organization_id
            where m.user_id=:userId order by o.name,o.id
            """, nativeQuery = true)
    List<MineRow> mine(UUID userId);
    interface MineRow {
        UUID getId(); String getName(); String getCity(); String getMemberRole(); long getEventsCount();
    }

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