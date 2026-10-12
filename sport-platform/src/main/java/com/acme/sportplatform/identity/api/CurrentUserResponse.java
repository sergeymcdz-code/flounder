package com.acme.sportplatform.identity.api;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String status,
        List<String> roles
) {
}