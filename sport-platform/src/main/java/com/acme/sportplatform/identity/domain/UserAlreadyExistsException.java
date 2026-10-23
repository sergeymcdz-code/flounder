package com.acme.sportplatform.identity.domain;

import com.acme.sportplatform.common.exception.BusinessException;

public class UserAlreadyExistsException extends BusinessException {

    public UserAlreadyExistsException() {
        super("identity.user_already_exists", "Не удалось зарегистрироваться с этим email. Если это ваш адрес — восстановите пароль");
    }
}