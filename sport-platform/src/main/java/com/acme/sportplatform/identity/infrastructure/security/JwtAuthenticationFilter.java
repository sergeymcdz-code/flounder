package com.acme.sportplatform.identity.infrastructure.security;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.acme.sportplatform.identity.domain.ExpiredTokenException;
import com.acme.sportplatform.identity.domain.InvalidTokenException;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
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

            if (!(rolesClaim instanceof Collection<?> rawRoles)) {
                throw new InvalidTokenException();
            }

            List<String> roles = rawRoles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .filter(role -> !role.isBlank())
                    .toList();

            if (roles.isEmpty()) {
                throw new InvalidTokenException();
            }

            UUID userId;

            try {
                userId = UUID.fromString(userIdValue);
            } catch (IllegalArgumentException ex) {
                throw new InvalidTokenException();
            }

            List<GrantedAuthority> authorities = roles.stream()
                    .map(role -> role.startsWith("ROLE_")
                            ? role.substring("ROLE_".length())
                            : role)
                    .map(String::toUpperCase)
                    .map(role -> (GrantedAuthority)
                            new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            PlatformUserPrincipal principal = new PlatformUserPrincipal(
                    userId,
                    email,
                    "",
                    "active",
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