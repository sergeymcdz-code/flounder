package com.acme.sportplatform.identity.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.identity.api.ChangePasswordRequest;
import com.acme.sportplatform.identity.api.PasswordResetRequest;
import com.acme.sportplatform.identity.api.PasswordResetConfirmRequest;
import com.acme.sportplatform.identity.api.RefreshTokenRequest;
import com.acme.sportplatform.identity.api.AuthTokenResponse;
import com.acme.sportplatform.identity.api.CreateUserRequest;
import com.acme.sportplatform.identity.api.CurrentUserResponse;
import com.acme.sportplatform.identity.api.LoginRequest;
import com.acme.sportplatform.identity.api.UserResponse;
import com.acme.sportplatform.identity.application.CreateUserUseCase;
import com.acme.sportplatform.identity.application.GetCurrentUserUseCase;
import com.acme.sportplatform.identity.application.PasswordService;
import com.acme.sportplatform.identity.application.AuthSessionService;
import com.acme.sportplatform.identity.application.LoginUseCase;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final com.acme.sportplatform.identity.application.AccountRequestLimiter accountLimits;
    private final LoginUseCase loginUseCase;
    private final PasswordService passwords;
    private final AuthSessionService sessions;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final CreateUserUseCase createUserUseCase;

    public AuthController(
            LoginUseCase loginUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            CreateUserUseCase createUserUseCase,
            PasswordService passwords,
            AuthSessionService sessions,
            com.acme.sportplatform.identity.application.AccountRequestLimiter accountLimits
    ) {
        this.accountLimits = accountLimits;
        this.loginUseCase = loginUseCase;
        this.passwords = passwords;
        this.sessions = sessions;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.createUserUseCase = createUserUseCase;
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        return loginUseCase.execute(request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody CreateUserRequest request, HttpServletRequest servletRequest
    ) {
        accountLimits.assertRegistrationAllowed(request.email(), servletRequest.getRemoteAddr());
        return createUserUseCase.execute(request);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal PlatformUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwords.change(principal.getUserId(), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        if (accountLimits.allowReset(request.email(), servletRequest.getRemoteAddr()))
            passwords.requestReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwords.confirmReset(request.token(), request.newPassword());
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest servletRequest) {
        return sessions.refresh(request.refreshToken(), servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        sessions.logout(request.refreshToken());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal PlatformUserPrincipal principal) {
        sessions.logoutAll(principal.getUserId());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return getCurrentUserUseCase.execute(principal);
    }
}