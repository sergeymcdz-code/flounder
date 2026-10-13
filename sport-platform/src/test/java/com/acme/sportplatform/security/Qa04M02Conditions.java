package com.acme.sportplatform.security;

import java.util.Arrays;
import org.springframework.web.bind.annotation.PostMapping;

/** Endpoint detection avoids linking to unmerged M02-specific classes. */
public final class Qa04M02Conditions {
    private Qa04M02Conditions() {}
    public static boolean available() {
        return Arrays.stream(com.acme.sportplatform.identity.web.AuthController.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(PostMapping.class)).filter(java.util.Objects::nonNull)
                .anyMatch(a -> Arrays.asList(a.value()).contains("/refresh"));
    }
}
