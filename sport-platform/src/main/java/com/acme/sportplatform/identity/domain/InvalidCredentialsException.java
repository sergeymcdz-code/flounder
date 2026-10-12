package com.acme.sportplatform.identity.domain;

import com.acme.sportplatform.common.exception.BusinessException;

public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super("identity.invalid_credentials", "Invalid email or password");
    }
}