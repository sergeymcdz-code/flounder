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

import com.acme.sportplatform.common.web.RestAuthenticationEntryPoint;
import com.acme.sportplatform.common.web.UserBlockedException;
import com.acme.sportplatform.identity.domain.ExpiredTokenException;
import com.acme.sportplatform.identity.domain.InvalidTokenException;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;
    private final RestAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtService jwtService,
            UserRepository users,
            RestAuthenticationEntryPoint entryPoint) {
        this.users = users; this.entryPoint = entryPoint;
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

            // One fresh database read supplies both status and global roles, without a cache.
            var snapshot = users.findAuthenticationById(userId);
            var user = snapshot.isEmpty() ? null : snapshot.getFirst().getUser();
            if (user == null) throw new InsufficientAuthenticationException("Пользователь не найден");
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) throw new UserBlockedException();
            // Global authority changes take effect immediately; scoped assignments are not global privileges.
            List<GrantedAuthority> authorities = snapshot.stream().map(row -> row.getRoleCode()).filter(java.util.Objects::nonNull)
                    .distinct()
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

        } catch (UserBlockedException | InsufficientAuthenticationException ex) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, ex);
            return;
        } catch (ExpiredTokenException ex) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new InsufficientAuthenticationException("JWT expired", ex));
            return;
        } catch (InvalidTokenException ex) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new InsufficientAuthenticationException("JWT invalid", ex));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
