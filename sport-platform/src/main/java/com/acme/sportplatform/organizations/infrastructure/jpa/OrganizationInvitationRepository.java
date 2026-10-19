package com.acme.sportplatform.organizations.infrastructure.jpa;

import com.acme.sportplatform.organizations.api.OrganizationInvitationResponse;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationInvitationRepository {
    private final JdbcTemplate jdbc;
    public OrganizationInvitationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Invitation(UUID id, UUID organizationId, String email, String memberRole, String status,
            OffsetDateTime expiresAt, OffsetDateTime createdAt) {
        public OrganizationInvitationResponse response() {
            return new OrganizationInvitationResponse(id, organizationId, email, memberRole, status, expiresAt, createdAt);
        }
    }
    private static final RowMapper<Invitation> MAPPER = (rs, n) -> new Invitation(
            rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getString("email"),
            rs.getString("member_role"), rs.getString("status"), rs.getObject("expires_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class));
    public void lockOrganization(UUID id) {
        jdbc.queryForList("select id from organizations where id=? for update", id);
    }
    public void expire(UUID organizationId, OffsetDateTime now) {
        jdbc.update("update organization_invitations set status='EXPIRED' where organization_id=? and status='PENDING' and expires_at<=?", organizationId, now);
    }
    public boolean pending(UUID organizationId, String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from organization_invitations where organization_id=? and lower(email)=lower(?) and status='PENDING')", Boolean.class, organizationId, email));
    }
    public Invitation create(UUID organizationId, String email, String role, String hash, UUID actor, OffsetDateTime now) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into organization_invitations(id,organization_id,email,member_role,token_hash,invited_by,expires_at,created_at) values (?,?,?,?,?,?,?,?)",
                id, organizationId, email, role, hash, actor, now.plusDays(7), now);
        return new Invitation(id, organizationId, email, role, "PENDING", now.plusDays(7), now);
    }
    public Optional<Invitation> byHash(String hash) {
        return jdbc.query("select * from organization_invitations where token_hash=?", MAPPER, hash).stream().findFirst();
    }
    public Optional<Invitation> byId(UUID organizationId, UUID id) {
        return jdbc.query("select * from organization_invitations where organization_id=? and id=? for update", MAPPER, organizationId, id).stream().findFirst();
    }
    public List<OrganizationInvitationResponse> list(UUID organizationId, String status) {
        return jdbc.query("select * from organization_invitations where organization_id=?" + (status == null ? "" : " and status=?") + " order by created_at desc,id",
                MAPPER, status == null ? new Object[]{organizationId} : new Object[]{organizationId, status}).stream().map(Invitation::response).toList();
    }
    public void revoke(UUID id) { jdbc.update("update organization_invitations set status='REVOKED' where id=?", id); }
    public void accept(UUID id, UUID userId, OffsetDateTime now) {
        jdbc.update("update organization_invitations set status='ACCEPTED',accepted_by=?,accepted_at=? where id=?", userId, now, id);
    }
}
