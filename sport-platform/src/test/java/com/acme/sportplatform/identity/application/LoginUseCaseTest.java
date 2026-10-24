package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.web.GlobalExceptionHandler;
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
import org.junit.jupiter.api.Test;
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
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final LoginAttemptLimiter limiter = mock(LoginAttemptLimiter.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var login = new LoginUseCase(manager, users, sessions, limiter, encoder);
        mvc = standaloneSetup(new AuthController(login, mock(GetCurrentUserUseCase.class), mock(CreateUserUseCase.class), mock(PasswordService.class), mock(AuthSessionService.class), mock(AccountRequestLimiter.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test
    void successfulAuthenticationClearsEmailFailuresBeforeIssuingTheSession() throws Exception {
        var principal = new PlatformUserPrincipal(UUID.randomUUID(), "user@qa04.example", "test-hash", "active", List.of());
        when(users.loadUserByUsername("user@qa04.example")).thenReturn(principal);
        when(sessions.issue(principal, null)).thenReturn(new com.acme.sportplatform.identity.api.AuthTokenResponse("access", "Bearer", 7200, "refresh"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@qa04.example\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().value("sportgate_refresh", "refresh"))
                .andExpect(cookie().httpOnly("sportgate_refresh", true))
                .andExpect(cookie().secure("sportgate_refresh", true));
        var order = inOrder(manager, limiter, sessions);
        order.verify(manager).authenticate(any());
        order.verify(limiter).succeeded("user@qa04.example", "127.0.0.1");
        order.verify(sessions).issue(principal, null);
        verify(limiter, never()).failed(anyString(), anyString());
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
        verifyNoInteractions(users, sessions, encoder);
        verify(limiter, never()).succeeded(anyString(), anyString());
        verify(limiter, never()).failed(anyString(), anyString());
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
        verifyNoInteractions(users, sessions, encoder);
        verify(limiter, never()).succeeded(anyString(), anyString());
        verify(limiter).failed("user@qa04.example", "127.0.0.1");
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
        verifyNoInteractions(sessions);
        verify(limiter, never()).succeeded(anyString(), anyString());
        verify(limiter, correctPassword ? never() : times(1)).failed("user@qa04.example", "127.0.0.1");
    }
}
