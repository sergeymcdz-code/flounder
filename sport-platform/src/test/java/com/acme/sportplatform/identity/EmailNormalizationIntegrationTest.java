package com.acme.sportplatform.identity;

import com.acme.sportplatform.common.MailSender;
import com.acme.sportplatform.identity.application.CreateUserUseCase;
import com.acme.sportplatform.identity.api.CreateUserRequest;
import com.acme.sportplatform.identity.application.PasswordService;
import com.acme.sportplatform.identity.application.UserLookupService;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserDetailsService;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailNormalizationIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired CreateUserUseCase create;
    @Autowired PasswordService passwords;
    @Autowired UserLookupService lookup;
    @Autowired PlatformUserDetailsService details;
    @Autowired DataSource dataSource;
    @MockitoBean MailSender mail;

    CreateUserRequest request(String email) {
        return new CreateUserRequest(email, "Initial123!", null, "Иван", "Петров", null,
                LocalDate.of(2000, 1, 1), "M", null, null, null);
    }

    @Test
    void normalizedAddressIsUsedForRegistrationLoginLookupAndReset() {
        String email = "case-" + UUID.randomUUID() + "@example.org";
        var user = create.execute(request("  " + email.toUpperCase(java.util.Locale.ROOT) + "  "));
        assertThat(user.email()).isEqualTo(email);
        assertThatThrownBy(() -> create.execute(request(email.toUpperCase(java.util.Locale.ROOT))))
                .isInstanceOf(com.acme.sportplatform.identity.domain.UserAlreadyExistsException.class);
        assertThat(details.loadUserByUsername(" " + email.toUpperCase(java.util.Locale.ROOT))).isNotNull();
        assertThat(lookup.findByEmail(" " + email.toUpperCase(java.util.Locale.ROOT)).orElseThrow().id()).isEqualTo(user.id());
        passwords.requestReset(email);
        verify(mail).sendPasswordReset(eq(email), any(java.util.UUID.class));
    }

    @Test
    void migrationRejectsDuplicatesAndLowercasesUniqueAddresses() throws Exception {
        String migration = new ClassPathResource("db/migration/V051__users_email_lower_unique.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            sql.execute("create temporary table users (email text not null)");
            sql.execute("insert into users values ('Victim@x'), ('victim@x')");
            var savepoint = connection.setSavepoint();
            assertThatThrownBy(() -> sql.execute(migration)).hasMessageContaining("Устраните дубли вручную");
            connection.rollback(savepoint);
            try (var rows = sql.executeQuery("select email from users where email='Victim@x'")) {
                rows.next(); assertThat(rows.getString(1)).isEqualTo("Victim@x");
            }
            sql.execute("delete from users where email='victim@x'");
            sql.execute(migration);
            try (var rows = sql.executeQuery("select email, tokens_valid_after from users")) {
                rows.next(); assertThat(rows.getString(1)).isEqualTo("victim@x");
                assertThat(rows.getObject(2)).isNull();
            }
            assertThatThrownBy(() -> sql.execute("insert into users(email) values ('VICTIM@x')"))
                    .hasMessageContaining("ux_users_email_lower");
            connection.rollback();
        }
    }
}
