package com.acme.sportplatform.identity.infrastructure.security;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.acme.sportplatform.common.web.RestAuthenticationEntryPoint;
import com.acme.sportplatform.identity.domain.ExpiredTokenException;
import com.acme.sportplatform.identity.domain.InvalidTokenException;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;
    private final UserRoleAssignmentRepository assignments;
    private final RestAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtService jwtService,
            UserRepository users,
            UserRoleAssignmentRepository assignments,
            RestAuthenticationEntryPoint entryPoint) {
        this.users = users; this.assignments = assignments; this.entryPoint = entryPoint;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = header.substring(7).trim();

            if (token.isBlank()) {
                throw new InvalidTokenException();
            }

            Claims claims = jwtService.parse(token);

            String userIdValue = claims.get("uid", String.class);
            String email = claims.getSubject();
            Object rolesClaim = claims.get("roles");

            if (userIdValue == null || userIdValue.isBlank()) {
                throw new InvalidTokenException();
            }

            if (email == null || email.isBlank()) {
                throw new InvalidTokenException();
            }

            if (!(rolesClaim instanceof Collection<?>)) {
                throw new InvalidTokenException();
            }

            UUID userId;

            try {
                userId = UUID.fromString(userIdValue);
            } catch (IllegalArgumentException ex) {
                throw new InvalidTokenException();
            }

            var user = users.findById(userId).orElse(null);
            if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response, new DisabledException(
                        user == null ? "identity.user_not_found" : "identity.user_blocked"));
                return;
            }
            // Global authority changes take effect immediately; scoped assignments are not global privileges.
            List<GrantedAuthority> authorities = assignments.findGlobalRoleCodesByUserId(userId).stream()
                    .map(role -> role.startsWith("ROLE_")
                            ? role.substring("ROLE_".length())
                            : role)
                    .map(String::toUpperCase)
                    .map(role -> (GrantedAuthority)
                            new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            PlatformUserPrincipal principal = new PlatformUserPrincipal(
                    userId,
                    user.getEmail(),
                    "",
                    user.getStatus(),
                    authorities
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (ExpiredTokenException ex) {
            SecurityContextHolder.clearContext();
            throw new InsufficientAuthenticationException("JWT expired", ex);
        } catch (InvalidTokenException ex) {
            SecurityContextHolder.clearContext();
            throw new InsufficientAuthenticationException("JWT invalid", ex);
        }
    }
}
