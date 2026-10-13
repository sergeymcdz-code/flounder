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
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, mock(com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository.class),
                mock(com.acme.sportplatform.common.web.RestAuthenticationEntryPoint.class));

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

    @Test
    void missingUserGetsOrdinaryAuthenticationErrorWithoutContinuingTheRequest() throws ServletException, IOException {
        var jwtService = mock(JwtService.class);
        var users = mock(com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository.class);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var filter = new JwtAuthenticationFilter(jwtService, users, new com.acme.sportplatform.common.web.RestAuthenticationEntryPoint(mapper));
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/auth/me");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        var id = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer missing-user-token");
        when(jwtService.parse("missing-user-token")).thenReturn(io.jsonwebtoken.Jwts.claims()
                .subject("missing@test.example").add("uid", id.toString()).add("roles", java.util.List.of("participant")).build());
        when(users.findAuthenticationById(id)).thenReturn(java.util.List.of());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(mapper.readTree(response.getContentAsByteArray()).get("code").asText()).isEqualTo("authentication_required");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        org.mockito.Mockito.verifyNoInteractions(chain);
    }
}