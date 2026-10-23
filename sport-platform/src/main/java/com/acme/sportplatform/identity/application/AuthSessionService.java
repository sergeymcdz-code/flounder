package com.acme.sportplatform.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.identity.api.AuthTokenResponse;
import com.acme.sportplatform.identity.domain.InvalidRefreshTokenException;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;
import com.acme.sportplatform.identity.infrastructure.security.*;

@Service
public class AuthSessionService {
    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final UserRoleAssignmentRepository roles;
    private final PlatformUserDetailsService details;
    private final JwtService jwt;
    private final Clock clock;

    public AuthSessionService(UserRepository users, RefreshTokenRepository tokens,
            UserRoleAssignmentRepository roles, PlatformUserDetailsService details, JwtService jwt,
            @Qualifier("identityClock") Clock clock) {
        this.users = users; this.tokens = tokens; this.roles = roles;
        this.details = details; this.jwt = jwt; this.clock = clock;
    }

    @Transactional
    public AuthTokenResponse issue(PlatformUserPrincipal principal, String userAgent) {
        UserEntity user = users.findByIdForUpdate(principal.getUserId()).orElseThrow(this::invalidToken);
        assertActive(user);
        return newPair(user, userAgent);
    }

    // A reused token must revoke sessions even though the request returns 401.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokenResponse refresh(String rawToken, String userAgent) {
        String hash = OpaqueTokens.hash(rawToken);
        var snapshot = tokens.findUserIdByTokenHash(hash).orElseThrow(this::invalidToken);
        UserEntity user = users.findByIdForUpdate(snapshot).orElseThrow(this::invalidToken);
        var token = tokens.findForUpdate(hash).orElseThrow(this::invalidToken);
        if (token.getRevokedAt() != null) {
            user.revokeAccessTokens(clock.instant());
            tokens.invalidateAll(user.getId(), clock.instant());
            throw invalidToken();
        }
        if (!token.getExpiresAt().isAfter(clock.instant())) throw invalidToken();
        assertActive(user);
        token.setRevokedAt(clock.instant());
        return newPair(user, userAgent);
    }

    @Transactional
    public void logout(String rawToken) {
        String hash = OpaqueTokens.hash(rawToken);
        var snapshot = tokens.findUserIdByTokenHash(hash);
        if (snapshot.isEmpty()) return;
        if (users.findByIdForUpdate(snapshot.get()).isEmpty()) return;
        tokens.findForUpdate(hash).filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(clock.instant()));
    }

    @Transactional
    public void logoutAll(UUID userId) {
        var user = users.findByIdForUpdate(userId).orElseThrow(this::invalidToken);
        user.revokeAccessTokens(clock.instant());
        tokens.invalidateAll(userId, clock.instant());
    }

    private AuthTokenResponse newPair(UserEntity user, String userAgent) {
        String raw = OpaqueTokens.generate();
        var token = new RefreshTokenEntity();
        token.setUserId(user.getId()); token.setTokenHash(OpaqueTokens.hash(raw));
        token.setCreatedAt(clock.instant()); token.setExpiresAt(clock.instant().plus(Duration.ofDays(30)));
        token.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(512, userAgent.length())));
        tokens.save(token);
        var principal = (PlatformUserPrincipal) details.loadUserByUsername(user.getEmail());
        return new AuthTokenResponse(jwt.generateAccessToken(principal, roles.findGlobalRoleCodesByUserId(user.getId()), user.getTokensValidAfter()),
                "Bearer", jwt.accessTokenTtlSeconds(), raw);
    }

    private void assertActive(UserEntity user) {
        if (!"active".equalsIgnoreCase(user.getStatus()))
            throw new BusinessException("identity.user_blocked", "Пользователь заблокирован. Обратитесь к администратору");
    }

    private InvalidRefreshTokenException invalidToken() {
        return new InvalidRefreshTokenException();
    }
}
