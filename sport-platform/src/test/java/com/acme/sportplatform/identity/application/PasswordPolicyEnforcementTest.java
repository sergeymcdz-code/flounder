package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.MailSender;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.api.CreateUserRequest;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordPolicyEnforcementTest {
    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh", "12345678", "a1", "Пароль123Пароль123Пароль123Пароль123Пароль123"})
    void registrationAndAdministratorUseTheSharedPolicyBeforeWriting(String password) {
        var users = mock(UserRepository.class);
        var profiles = mock(ProfileRepository.class);
        var roles = mock(RoleRepository.class);
        var assignments = mock(UserRoleAssignmentRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var resets = mock(PasswordResetTokenRepository.class);
        var refresh = mock(RefreshTokenRepository.class);
        var create = new CreateUserUseCase(users, profiles, roles, assignments, encoder);
        var passwords = new PasswordService(users, resets, refresh, encoder, mock(MailSender.class),
                Clock.systemUTC(), "http://localhost");
        var request = new CreateUserRequest("user@example.org", password, null, "Иван", "Петров", null,
                LocalDate.of(2000, 1, 1), "M", null, null, null);
        assertThatThrownBy(() -> create.execute(request)).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("identity.password_weak"));
        assertThatThrownBy(() -> passwords.changeByAdministrator(UUID.randomUUID(), password))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("identity.password_weak"));
        verifyNoInteractions(users, profiles, roles, assignments, encoder, resets, refresh);
    }
}
