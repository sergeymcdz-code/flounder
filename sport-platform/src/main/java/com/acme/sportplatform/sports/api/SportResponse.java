package com.acme.sportplatform.sports.api;

import java.util.UUID;

public record SportResponse(
        UUID id,
        String code,
        String name,
        boolean isActive
) {
}