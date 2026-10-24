package com.acme.sportplatform.security;

import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogOwnershipMigrationTest extends AbstractPostgresIntegrationTest {
    @Autowired DataSource dataSource;

    @Test void upgradeKeepsLegacyCatalogSharedAndScrubsOldResetSecrets() throws Exception {
        String schema = "secb5_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(dataSource);
        admin.execute("create schema " + schema);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("51").load().migrate();
            try (var connection = dataSource.getConnection()) {
                connection.setSchema(schema);
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                UUID user = UUID.randomUUID(), venue = UUID.randomUUID();
                jdbc.update("insert into users(id,email,password_hash,status) values (?,'legacy@example.test','test-unused','active')", user);
                jdbc.update("insert into venues(id,name,timezone) values (?,'Общая площадка','UTC')", venue);
                jdbc.update("insert into password_reset_tokens(id,user_id,token_hash,expires_at,created_at) values (?,?,?,now()+interval '1 hour',now())",
                        UUID.randomUUID(), user, "a".repeat(64));
                for (String status : java.util.List.of("PENDING", "FAILED"))
                    jdbc.update("insert into notification_outbox(id,recipient_email,template_code,payload,delivery_key,created_at,next_attempt_at,status) values (?,'legacy@example.test','password_reset',?::jsonb,?,now(),now(),?)",
                            UUID.randomUUID(), "{\"body\":\"/reset-password?token=test-legacy-secret\"}", status, status);
                Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("55").load().migrate();
                assertThat(jdbc.queryForObject("select organization_id is null from venues where id=?", Boolean.class, venue)).isTrue();
                assertThat(jdbc.queryForObject("select used_at is not null from password_reset_tokens where user_id=?", Boolean.class, user)).isTrue();
                assertThat(jdbc.queryForList("select payload::text from notification_outbox", String.class)).containsOnly("{}");
                assertThat(jdbc.queryForList("select status from notification_outbox", String.class)).containsOnly("FAILED");
                connection.setSchema("public");
            }
        } finally { admin.execute("drop schema " + schema + " cascade"); }
    }
}
