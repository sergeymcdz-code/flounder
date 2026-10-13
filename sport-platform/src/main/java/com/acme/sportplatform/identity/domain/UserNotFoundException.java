package com.acme.sportplatform.identity.domain;

import com.acme.sportplatform.common.exception.BusinessException;
import java.util.UUID;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(UUID userId) {
        super("identity.user_not_found", "Пользователь с ID '%s' не найден".formatted(userId));
    }
}