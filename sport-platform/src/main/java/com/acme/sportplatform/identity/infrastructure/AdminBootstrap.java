package com.acme.sportplatform.identity.infrastructure;

import com.acme.sportplatform.identity.domain.PasswordPolicy;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.*;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleAssignmentRepository assignments;
    private final PasswordEncoder encoder;
    private final String email, password;
    public AdminBootstrap(UserRepository users, RoleRepository roles, UserRoleAssignmentRepository assignments,
            PasswordEncoder encoder, @Value("${APP_ADMIN_EMAIL:}") String email, @Value("${APP_ADMIN_PASSWORD:}") String password) {
        this.users=users; this.roles=roles; this.assignments=assignments;
        this.encoder=encoder; this.email=com.acme.sportplatform.identity.EmailAddress.normalize(email); this.password=password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) return;
        if (users.existsByEmail(email)) {
            LoggerFactory.getLogger(AdminBootstrap.class).info("bootstrap admin exists, password untouched"); return;
        }
        PasswordPolicy.validate(password);
        var role=roles.findByCode("platform_admin").orElseThrow();
        var user=new UserEntity(); user.setEmail(email); user.setStatus("active"); user.setPasswordHash(encoder.encode(password));
        user=users.save(user);
        var assignment=new UserRoleAssignmentEntity(); assignment.setUserId(user.getId()); assignment.setRoleId(role.getId());
        assignments.save(assignment);
        LoggerFactory.getLogger(AdminBootstrap.class).info("bootstrap admin created");
    }
}
