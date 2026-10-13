package com.acme.sportplatform.identity.application;

import java.util.List;

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
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.acme.sportplatform.identity.infrastructure.security.JwtService;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserDetailsService;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;

@Service
public class LoginUseCase {

    private final AuthenticationManager authenticationManager;
    private final PlatformUserDetailsService userDetailsService;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public LoginUseCase(AuthenticationManager authenticationManager,
                        PlatformUserDetailsService userDetailsService,
                        UserRoleAssignmentRepository userRoleAssignmentRepository,
                        JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthTokenResponse execute(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (DisabledException ex) {
            PlatformUserPrincipal principal = (PlatformUserPrincipal) userDetailsService.loadUserByUsername(request.email());
            if (passwordEncoder.matches(request.password(), principal.getPassword())) {
                throw new BusinessException("identity.user_blocked", "Пользователь заблокирован. Обратитесь к администратору");
            }
            throw new InvalidCredentialsException();
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            throw new InvalidCredentialsException();
        } catch (AccountStatusException ex) {
            // Other account restrictions keep QA04's generic credential error.
            throw new InvalidCredentialsException();
        }

        PlatformUserPrincipal principal =
                (PlatformUserPrincipal) userDetailsService.loadUserByUsername(request.email());

        List<String> roles = userRoleAssignmentRepository.findGlobalRoleCodesByUserId(principal.getUserId());

        String token = jwtService.generateAccessToken(principal, roles);

        return new AuthTokenResponse(token, "Bearer", 7200);
    }
}
