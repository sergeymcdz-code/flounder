package com.acme.sportplatform.identity;

import java.util.Optional;
import java.util.UUID;

/** Generates reset secrets only while delivering mail; callers must not persist the result. */
public interface PasswordResetMailLookup {
    record Message(String subject, String body) {}
    Optional<Message> prepare(UUID requestId, String recipient);
}
