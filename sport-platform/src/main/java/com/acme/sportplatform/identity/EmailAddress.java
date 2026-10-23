package com.acme.sportplatform.identity;

import java.util.Locale;

public final class EmailAddress {
    private EmailAddress() { }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
