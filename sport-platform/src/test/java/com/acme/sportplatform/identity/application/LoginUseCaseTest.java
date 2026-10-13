package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.web.GlobalExceptionHandler;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.acme.sportplatform.identity.infrastructure.security.JwtService;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserDetailsService;
import com.acme.sportplatform.identity.web.AuthController;
import java.util.stream.Stream;
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
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var login = new LoginUseCase(manager, users, roles, jwt);
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
        verifyNoInteractions(users, roles, jwt);
    }
    static Stream<AuthenticationException> credentialFailures() {
        return Stream.of(new BadCredentialsException("Wrong password"), new DisabledException("Disabled"),
                new LockedException("Locked"), new AccountExpiredException("Expired"), new CredentialsExpiredException("Expired credentials"));
    }
    @ParameterizedTest
    @MethodSource("credentialFailures")
    void badCredentialsAndAccountStatusRemain401(AuthenticationException failure) throws Exception {
        when(manager.authenticate(any())).thenThrow(failure);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@qa04.example\",\"password\":\"test-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("identity.invalid_credentials"));
        verifyNoInteractions(users, roles, jwt);
    }
}
