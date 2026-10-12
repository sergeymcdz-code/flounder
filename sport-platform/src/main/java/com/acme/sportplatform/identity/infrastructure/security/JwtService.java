package com.acme.sportplatform.identity.infrastructure.security;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.identity.domain.ExpiredTokenException;
import com.acme.sportplatform.identity.domain.InvalidTokenException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(PlatformUserPrincipal principal, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.accessTokenTtl());

        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("uid", principal.getUserId().toString())
                .claim("roles", roles)
                .signWith(signingKey())
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new ExpiredTokenException();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parse(token).get("uid", String.class));
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    public List<String> extractRoles(String token) {
        Object rolesClaim = parse(token).get("roles");

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

        return roles;
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.secret());

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must decode to at least 32 bytes for HS256."
            );
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}