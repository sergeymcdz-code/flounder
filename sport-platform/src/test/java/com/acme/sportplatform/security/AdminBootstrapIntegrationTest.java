package com.acme.sportplatform.security;

import com.acme.sportplatform.identity.infrastructure.AdminBootstrap;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AdminBootstrapIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleAssignmentRepository assignments;
    @Autowired PasswordEncoder encoder;
    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(users, roles, assignments, encoder, email, password);
    }
    @Test void createsAnActiveAdminWhenTheEmailIsAbsent() {
        bootstrap("bootstrap@qa04.example", "Bootstrap123!").run(null);
        var user=users.findByEmail("bootstrap@qa04.example").orElseThrow();
        assertThat(user.getStatus()).isEqualTo("active");
        assertThat(encoder.matches("Bootstrap123!",user.getPasswordHash())).isTrue();
        assertThat(assignments.findGlobalRoleCodesByUserId(user.getId())).containsExactly("platform_admin");
    }
    @Test void repeatedRunNeverChangesTheExistingPassword() {
        bootstrap("repeat@qa04.example", "Bootstrap123!").run(null);
        String original=users.findByEmail("repeat@qa04.example").orElseThrow().getPasswordHash();
        bootstrap("repeat@qa04.example", "Different456!").run(null);
        assertThat(users.findByEmail("repeat@qa04.example").orElseThrow().getPasswordHash()).isEqualTo(original);
    }
    @Test void emptyEnvironmentCreatesNothing() {
        long before=users.count();
        bootstrap("", "").run(null);
        bootstrap("missing-password@qa04.example", "").run(null);
        bootstrap("", "Bootstrap123!").run(null);
        assertThat(users.count()).isEqualTo(before);
    }
}
