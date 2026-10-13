package com.acme.sportplatform.identity;

import java.util.UUID;

public interface UserRoleLookup {
    void requireUser(UUID userId);
    void ensureOrganizer(UUID userId);
}
