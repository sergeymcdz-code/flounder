package com.acme.sportplatform.common.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp,
        List<FieldViolation> violations
) {
    public record FieldViolation(String field, String message) {}
}