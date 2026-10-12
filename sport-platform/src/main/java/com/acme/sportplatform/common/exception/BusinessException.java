package com.acme.sportplatform.common.exception;

public class BusinessException extends RuntimeException {

    private final String code;
    private final java.util.List<com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation>
            violations;

    public BusinessException(String code, String message) {
        this(code, message, java.util.List.of());
    }

    public BusinessException(
            String code,
            String message,
            java.util.List<com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation>
                    violations) {
        super(message);
        this.code = code;
        this.violations = java.util.List.copyOf(violations);
    }

    public java.util.List<com.acme.sportplatform.common.api.ApiErrorResponse.FieldViolation>
            getViolations() {
        return violations;
    }

    public String getCode() {
        return code;
    }
}
