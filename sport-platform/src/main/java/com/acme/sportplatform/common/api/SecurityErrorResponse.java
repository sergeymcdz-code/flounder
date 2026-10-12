package com.acme.sportplatform.common.api;

import java.time.OffsetDateTime;

public record SecurityErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp,
        String path
) {
}