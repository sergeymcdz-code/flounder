package com.acme.sportplatform.identity.domain;

import com.acme.sportplatform.common.exception.BusinessException;
import java.util.UUID;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(UUID userId) {
        super("identity.user_not_found", "User with id '%s' was not found".formatted(userId));
    }
}