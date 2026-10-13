package com.acme.sportplatform.common.exception;

public class TooManyRequestsException extends BusinessException {
    private final long retryAfter;

    public TooManyRequestsException(String code, String message, long retryAfter) {
        super(code, message);
        this.retryAfter = retryAfter;
    }

    public long getRetryAfter() { return retryAfter; }
}
