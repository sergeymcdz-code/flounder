package com.acme.sportplatform.identity.infrastructure.security;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import com.acme.sportplatform.common.web.RestAuthenticationEntryPoint;
import com.acme.sportplatform.identity.UserMembershipLookup;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import com.acme.sportplatform.identity.domain.InvalidTokenException;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class JwtAuthenticationFilterTest {

    static java.util.stream.Stream<RuntimeException> downstreamFailures() {
        return java.util.stream.Stream.of(
                new com.acme.sportplatform.common.web.UserBlockedException(),
                new InsufficientAuthenticationException("Downstream authentication failure"),
                new com.acme.sportplatform.identity.domain.ExpiredTokenException(),
                new InvalidTokenException());
    }

    @ParameterizedTest
    @MethodSource("downstreamFailures")
    void downstreamExceptionsAreNotHandledAsJwtAuthenticationFailures(RuntimeException failure) throws Exception {
        var jwtService = mock(JwtService.class);
        var users = mock(UserMembershipLookup.class);
        var entryPoint = mock(RestAuthenticationEntryPoint.class);
        var filter = new JwtAuthenticationFilter(jwtService, users, entryPoint);
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/auth/me");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        var id = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer active-user-token");
        when(jwtService.parse("active-user-token")).thenReturn(io.jsonwebtoken.Jwts.claims()
                .subject("active@test.example").add("uid", id.toString()).add("roles", java.util.List.of("participant")).build());
        var user = new com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity();
        user.setEmail("active@test.example");
        user.setStatus("active");
        var row = mock(UserMembershipLookup.AuthenticationRow.class);
        when(row.getEmail()).thenReturn(user.getEmail());
        when(row.getStatus()).thenReturn(user.getStatus());
        when(row.getRoleCode()).thenReturn("participant");
        when(users.authenticationOf(id)).thenReturn(java.util.List.of(row));
        doAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
            response.flushBuffer();
            throw failure;
        }).when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(failure);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(entryPoint);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTokenWithoutRolesClaim() throws ServletException, IOException {
        JwtService jwtService = mock(JwtService.class);
        var entryPoint = mock(RestAuthenticationEntryPoint.class);
        var users = mock(UserMembershipLookup.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, users, entryPoint);

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

        filter.doFilter(request, response, filterChain);
        var error = ArgumentCaptor.forClass(InsufficientAuthenticationException.class);
        verify(entryPoint).commence(eq(request), eq(response), error.capture());
        assertThat(error.getValue()).hasMessage("JWT invalid").hasCauseInstanceOf(InvalidTokenException.class);
        verifyNoInteractions(filterChain, users);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
    }
    @Test
    void missingUserGetsOrdinaryAuthenticationErrorWithoutContinuingTheRequest() throws ServletException, IOException {
        var jwtService = mock(JwtService.class);
        var users = mock(com.acme.sportplatform.identity.UserMembershipLookup.class);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var filter = new JwtAuthenticationFilter(jwtService, users, new com.acme.sportplatform.common.web.RestAuthenticationEntryPoint(mapper));
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/auth/me");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        var id = UUID.randomUUID();
        request.addHeader("Authorization", "Bearer missing-user-token");
        when(jwtService.parse("missing-user-token")).thenReturn(io.jsonwebtoken.Jwts.claims()
                .subject("missing@test.example").add("uid", id.toString()).add("roles", java.util.List.of("participant")).build());
        when(users.authenticationOf(id)).thenReturn(java.util.List.of());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(mapper.readTree(response.getContentAsByteArray()).get("code").asText()).isEqualTo("authentication_required");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        org.mockito.Mockito.verifyNoInteractions(chain);
    }
}
