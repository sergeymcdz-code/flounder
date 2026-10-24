package com.acme.sportplatform.organizations.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.organizations.api.AddOrganizationMemberRequest;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class OrganizationOwnerConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired OrganizationService organizations;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    UUID organizationId;
    List<UUID> owners;

    @BeforeEach
    void createCommittedOwners() {
        organizationId = UUID.randomUUID();
        owners = List.of(UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("insert into organizations(id,type,name,created_at,updated_at) values (?,'club','SEC-B1',now(),now())", organizationId);
        for (UUID owner : owners) {
            jdbc.update("insert into users(id,email,password_hash,status,created_at,updated_at) values (?,?,?,'active',now(),now())",
                    owner, owner + "@sec-b1.example", "test-only-hash");
            jdbc.update("insert into organization_members(id,organization_id,user_id,member_role,created_at) values (?,?,?,'owner',now())",
                    UUID.randomUUID(), organizationId, owner);
        }
    }

    @AfterEach
    void removeCommittedFixture() {
        jdbc.update("delete from organization_members where organization_id=?", organizationId);
        jdbc.update("delete from organizations where id=?", organizationId);
        for (UUID owner : owners) jdbc.update("delete from users where id=?", owner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "patch", "delete"})
    void simultaneousOwnerDeparturesLeaveExactlyOneOwner(String operation) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = owners.stream().map(owner -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                try {
                    switch (operation) {
                        case "post" -> organizations.addMember(organizationId, new AddOrganizationMemberRequest(owner, "member"), owner, false);
                        case "patch" -> organizations.updateMember(organizationId, owner, "admin", owner, false);
                        case "delete" -> organizations.deleteMember(organizationId, owner, owner, false);
                        default -> throw new AssertionError(operation);
                    }
                    return "ok";
                } catch (BusinessException ex) {
                    return ex.getCode();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("ok", "organizations.last_owner");
        }
        assertThat(jdbc.queryForObject("select count(*) from organization_members where organization_id=? and lower(member_role)='owner'",
                Long.class, organizationId)).isEqualTo(1);
    }

    @Test
    void auditContainsActorTargetAndRoleTransitionAfterCommit(CapturedOutput output) {
        organizations.updateMember(organizationId, owners.get(1), "admin", owners.get(0), false);
        assertThat(output.getOut()).contains("organization_role_changed organizationId=" + organizationId
                + " actorId=" + owners.get(0) + " targetUserId=" + owners.get(1) + " previousRole=owner nextRole=admin");
        organizations.deleteMember(organizationId, owners.get(1), owners.get(0), false);
        assertThat(output.getOut()).contains("previousRole=admin nextRole=null");
    }

    @Test
    void rolledBackAndRejectedChangesDoNotProduceAudit(CapturedOutput output) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            organizations.updateMember(organizationId, owners.get(1), "member", owners.get(0), false);
            status.setRollbackOnly();
        });
        jdbc.update("delete from organization_members where organization_id=? and user_id=?", organizationId, owners.get(1));
        assertThatThrownBy(() -> organizations.deleteMember(organizationId, owners.get(0), owners.get(0), false))
                .isInstanceOf(LastOrganizationOwnerException.class);
        assertThat(output.getOut()).doesNotContain("organization_role_changed organizationId=" + organizationId);
    }
}
