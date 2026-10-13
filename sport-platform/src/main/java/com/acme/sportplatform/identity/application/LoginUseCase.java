package com.acme.sportplatform.identity.application;

import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.api.AuthTokenResponse;
import com.acme.sportplatform.identity.api.LoginRequest;
import com.acme.sportplatform.identity.domain.InvalidCredentialsException;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserDetailsService;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;

@Service
public class LoginUseCase {

    private final AuthenticationManager authenticationManager;
    private final PlatformUserDetailsService userDetailsService;
    private final AuthSessionService sessions;
    private final LoginAttemptLimiter limiter;
    private final PasswordEncoder passwordEncoder;

    public LoginUseCase(AuthenticationManager authenticationManager,
                        PlatformUserDetailsService userDetailsService,
                        AuthSessionService sessions, LoginAttemptLimiter limiter, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.sessions = sessions;
        this.limiter = limiter;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthTokenResponse execute(LoginRequest request, String ip, String userAgent) {
        limiter.assertAllowed(request.email(), ip);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (DisabledException ex) {
            PlatformUserPrincipal principal = (PlatformUserPrincipal) userDetailsService.loadUserByUsername(request.email());
            if (passwordEncoder.matches(request.password(), principal.getPassword())) {
                throw new BusinessException("identity.user_blocked", "Пользователь заблокирован. Обратитесь к администратору");
            }
            limiter.failed(request.email(), ip);
            throw new InvalidCredentialsException();
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            limiter.failed(request.email(), ip);
            throw new InvalidCredentialsException();
        } catch (AccountStatusException ex) {
            // Other account restrictions keep QA04's generic credential error.
            limiter.failed(request.email(), ip);
            throw new InvalidCredentialsException();
        }

        PlatformUserPrincipal principal =
                (PlatformUserPrincipal) userDetailsService.loadUserByUsername(request.email());

        return sessions.issue(principal, userAgent);
    }
}
