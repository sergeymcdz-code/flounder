package com.acme.sportplatform.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractPostgresIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate membershipJdbc;

    protected java.util.List<com.acme.sportplatform.identity.UserMembershipLookup.Membership> memberships(java.util.UUID userId) {
        return membershipJdbc.query("""
                select m.organization_id, o.name, m.member_role from organization_members m
                join organizations o on o.id=m.organization_id where m.user_id=?
                """, (rs, row) -> new com.acme.sportplatform.identity.UserMembershipLookup.Membership(
                rs.getObject(1, java.util.UUID.class), rs.getString(2), rs.getString(3)), userId);
    }

    /** Seed explicit ownership for HTTP fixtures that previously relied on the event creator shortcut. */
    protected void ownerMembership(java.util.UUID organizationId, java.util.UUID userId) {
        membershipJdbc.update("insert into users(id,email,password_hash,status) values (?,?,'test-only-hash','active') on conflict(id) do nothing",
                userId, userId + "@membership.test.example");
        membershipJdbc.update("insert into organizations(id,type,name,created_at,updated_at) values (?,'club','Test club',now(),now()) on conflict(id) do nothing",
                organizationId);
        membershipJdbc.update("insert into organization_members(id,organization_id,user_id,member_role,created_at) values (?,?,?,'owner',now()) on conflict(organization_id,user_id) do nothing",
                java.util.UUID.randomUUID(), organizationId, userId);
    }

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("sport_platform_test")
                    .withUsername("sport_user")
                    .withPassword("sport_pass");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");

        registry.add("app.security.jwt.secret",() -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }
}