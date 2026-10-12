package com.acme.sportplatform.identity.domain;

import com.acme.sportplatform.common.exception.BusinessException;

public class UserAlreadyExistsException extends BusinessException {

    public UserAlreadyExistsException(String email) {
        super("identity.user_already_exists", "User with email '%s' already exists".formatted(email));
    }
}