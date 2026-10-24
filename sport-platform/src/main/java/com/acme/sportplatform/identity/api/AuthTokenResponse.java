package com.acme.sportplatform.identity.api;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        @com.fasterxml.jackson.annotation.JsonIgnore String refreshToken
) {
}