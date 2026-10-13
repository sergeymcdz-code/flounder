package com.acme.sportplatform.identity.infrastructure.security;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import com.acme.sportplatform.common.web.RestAuthenticationEntryPoint;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
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
        var entryPoint = mock(RestAuthenticationEntryPoint.class);
        var users = mock(UserRepository.class);
        var assignments = mock(UserRoleAssignmentRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, users, assignments, entryPoint);

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
        verifyNoInteractions(filterChain, users, assignments);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNull();
    }
}
