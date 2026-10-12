package com.acme.sportplatform.identity.api;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}