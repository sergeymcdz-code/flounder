package com.acme.sportplatform.identity.domain;

import com.acme.sportplatform.common.exception.BusinessException;

public class ExpiredTokenException extends BusinessException {

    public ExpiredTokenException() {
        super("identity.token_expired", "Access token has expired");
    }
}