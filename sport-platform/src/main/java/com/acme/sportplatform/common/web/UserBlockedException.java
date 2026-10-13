package com.acme.sportplatform.common.web;

import org.springframework.security.core.AuthenticationException;

public class UserBlockedException extends AuthenticationException {
    public UserBlockedException() {
        super("Пользователь заблокирован. Обратитесь к администратору");
    }

    public String getCode() { return "identity.user_blocked"; }
}
