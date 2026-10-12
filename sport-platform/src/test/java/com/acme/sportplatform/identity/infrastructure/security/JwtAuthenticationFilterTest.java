package com.acme.sportplatform.identity.infrastructure.security;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import com.acme.sportplatform.identity.domain.InvalidTokenException;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTokenWithoutRolesClaim() throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);
        Claims claims = mock(Claims.class);

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer valid-signed-token");

        when(jwtService.parse("valid-signed-token"))
                .thenReturn(claims);

        when(claims.get("uid", String.class))
                .thenReturn(UUID.randomUUID().toString());

        when(claims.getSubject())
                .thenReturn("participant@example.com");

        when(claims.get("roles"))
                .thenReturn(null);

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(InsufficientAuthenticationException.class)
                .hasMessage("JWT invalid")
                .hasCauseInstanceOf(InvalidTokenException.class);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
    }
}