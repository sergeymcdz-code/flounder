package com.acme.sportplatform.identity.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String phone,
        String status,
        OffsetDateTime createdAt
) {
}