package com.acme.sportplatform.identity.domain;

import com.acme.sportplatform.common.exception.BusinessException;

public class InvalidTokenException extends BusinessException {

    public InvalidTokenException() {
        super("identity.invalid_token", "Access token is invalid");
    }
}