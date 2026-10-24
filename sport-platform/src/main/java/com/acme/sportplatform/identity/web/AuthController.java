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
import org.springframework.web.bind.annotation.CookieValue;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import java.time.Duration;
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

    public static final String REFRESH_COOKIE = "sportgate_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

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
            HttpServletRequest servletRequest, HttpServletResponse response) {
        var pair = loginUseCase.execute(request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
        refreshCookie(response, pair.refreshToken(), Duration.ofDays(30));
        return pair;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody CreateUserRequest request, HttpServletRequest servletRequest
    ) {
        com.acme.sportplatform.identity.domain.PasswordPolicy.validate(request.password());
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
    public AuthTokenResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
            HttpServletRequest servletRequest, HttpServletResponse response) {
        if (token == null || token.isBlank()) throw new com.acme.sportplatform.identity.domain.InvalidRefreshTokenException();
        var pair = sessions.refresh(token, servletRequest.getHeader("User-Agent"));
        refreshCookie(response, pair.refreshToken(), Duration.ofDays(30));
        return pair;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token, HttpServletResponse response) {
        if (token != null && !token.isBlank()) sessions.logout(token);
        refreshCookie(response, "", Duration.ZERO);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal PlatformUserPrincipal principal, HttpServletResponse response) {
        sessions.logoutAll(principal.getUserId());
        refreshCookie(response, "", Duration.ZERO);
    }

    private void refreshCookie(HttpServletResponse response, String token, Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Lax").path(COOKIE_PATH).maxAge(maxAge).build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @GetMapping("/me")
    public CurrentUserResponse me(
            @AuthenticationPrincipal PlatformUserPrincipal principal
    ) {
        return getCurrentUserUseCase.execute(principal);
    }
}