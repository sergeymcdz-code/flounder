package com.acme.sportplatform.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Independent credentials for tests that need a real administrator login. */
public record TestAdmin(UUID id, String email) {
    public static final String PASSWORD = "Test-only-password1!";
    private static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    public static TestAdmin create(JdbcTemplate jdbc) {
        var admin = new TestAdmin(UUID.randomUUID(), UUID.randomUUID() + "@admin.test.example");
        jdbc.update("insert into users(id,email,password_hash,status) values (?,?,?,'active')",
                admin.id(), admin.email(), HASH);
        jdbc.update("insert into profiles(id,user_id,first_name,last_name,birth_date,gender) values (?,?,'Test','Admin','1990-01-01','M')",
                UUID.randomUUID(), admin.id());
        jdbc.update("insert into user_role_assignments(id,user_id,role_id) select ?,?,id from roles where code='platform_admin'",
                UUID.randomUUID(), admin.id());
        return admin;
    }
}
