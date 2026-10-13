package com.acme.sportplatform.common.exception;

public class BusinessException extends RuntimeException {

    private final String code;
    private final java.util.List<?>
            violations;

    public BusinessException(String code, String message) {
        this(code, message, java.util.List.of());
    }

    public BusinessException(
            String code,
            String message,
            java.util.List<?>
                    violations) {
        super(message);
        this.code = code;
        this.violations = java.util.List.copyOf(violations);
    }

    public java.util.List<?>
            getViolations() {
        return violations;
    }

    public String getCode() {
        return code;
    }
}
