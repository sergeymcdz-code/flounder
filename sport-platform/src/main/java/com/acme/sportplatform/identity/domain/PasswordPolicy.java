package com.acme.sportplatform.identity.domain;

import java.nio.charset.StandardCharsets;
import com.acme.sportplatform.common.exception.BusinessException;

public final class PasswordPolicy {
    private PasswordPolicy() { }

    public static void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().noneMatch(Character::isLetter)
                || password.codePoints().noneMatch(Character::isDigit)) {
            throw new BusinessException("identity.password_weak",
                    "Пароль должен содержать не менее 8 символов, буквы и цифры, и занимать не более 72 байт");
        }
    }
}
