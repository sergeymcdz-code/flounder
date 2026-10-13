package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.web.GlobalExceptionHandler;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.acme.sportplatform.identity.infrastructure.security.JwtService;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserDetailsService;
import com.acme.sportplatform.identity.web.AuthController;
import java.util.stream.Stream;
import java.util.List;
import java.util.UUID;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class LoginUseCaseTest {
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final PlatformUserDetailsService users = mock(PlatformUserDetailsService.class);
    private final UserRoleAssignmentRepository roles = mock(UserRoleAssignmentRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var login = new LoginUseCase(manager, users, roles, jwt, encoder);
        mvc = standaloneSetup(new AuthController(login, mock(GetCurrentUserUseCase.class), mock(CreateUserUseCase.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    static Stream<AuthenticationException> infrastructureFailures() {
        return Stream.of(new InternalAuthenticationServiceException("Storage unavailable"),
                new AuthenticationServiceException("Provider unavailable"));
    }
    @ParameterizedTest
    @MethodSource("infrastructureFailures")
    void infrastructureFailureIs500InternalErrorRatherThan401(AuthenticationException failure) throws Exception {
        when(manager.authenticate(any())).thenThrow(failure);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@qa04.example\",\"password\":\"test-password\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.message").value("Непредвиденная внутренняя ошибка сервера"));
        verifyNoInteractions(users, roles, jwt, encoder);
    }
    static Stream<AuthenticationException> credentialFailures() {
        return Stream.of(new BadCredentialsException("Wrong password"), new UsernameNotFoundException("Unknown user"),
                new LockedException("Locked"), new AccountExpiredException("Expired"), new CredentialsExpiredException("Expired credentials"));
    }
    @ParameterizedTest
    @MethodSource("credentialFailures")
    void badCredentialsAndAccountStatusRemain401(AuthenticationException failure) throws Exception {
        when(manager.authenticate(any())).thenThrow(failure);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@qa04.example\",\"password\":\"test-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("identity.invalid_credentials"));
        verifyNoInteractions(users, roles, jwt, encoder);
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void disabledAccountsRevealTheirStatusOnlyAfterPasswordVerification(boolean correctPassword) throws Exception {
        when(manager.authenticate(any())).thenThrow(new DisabledException("Disabled"));
        when(users.loadUserByUsername("user@qa04.example")).thenReturn(new PlatformUserPrincipal(
                UUID.randomUUID(), "user@qa04.example", "test-hash", "blocked", List.of()));
        when(encoder.matches("test-password", "test-hash")).thenReturn(correctPassword);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@qa04.example\",\"password\":\"test-password\"}"))
                .andExpect(status().is(correctPassword ? 403 : 401))
                .andExpect(jsonPath("$.code").value(correctPassword ? "identity.user_blocked" : "identity.invalid_credentials"));
        verify(encoder).matches("test-password", "test-hash");
        verifyNoInteractions(roles, jwt);
    }
}
