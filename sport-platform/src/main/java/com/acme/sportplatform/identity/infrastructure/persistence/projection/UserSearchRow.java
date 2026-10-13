package com.acme.sportplatform.identity.infrastructure.persistence.projection;

import java.time.Instant;
import java.util.UUID;

public interface UserSearchRow {
    UUID getId();
    String getEmail();
    String getStatus();
    Instant getCreatedAt();
    long getTotal();
}
