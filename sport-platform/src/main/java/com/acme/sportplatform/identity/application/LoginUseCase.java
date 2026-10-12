package com.acme.sportplatform.identity.application;

import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

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

    public LoginUseCase(AuthenticationManager authenticationManager,
                        PlatformUserDetailsService userDetailsService,
                        UserRoleAssignmentRepository userRoleAssignmentRepository,
                        JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.jwtService = jwtService;
    }

    public AuthTokenResponse execute(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException();
        }

        PlatformUserPrincipal principal =
                (PlatformUserPrincipal) userDetailsService.loadUserByUsername(request.email());

        List<String> roles = userRoleAssignmentRepository.findRoleCodesByUserId(principal.getUserId());

        String token = jwtService.generateAccessToken(principal, roles);

        return new AuthTokenResponse(token, "Bearer", 7200);
    }
}