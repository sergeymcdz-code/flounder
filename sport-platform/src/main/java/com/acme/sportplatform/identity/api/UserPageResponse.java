package com.acme.sportplatform.identity.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserPageResponse(List<Item> items, int page, int size, long total) {
    public record Item(UUID id, String email, String firstName, String lastName, String status,
                       List<String> roles, OffsetDateTime createdAt) {}
}
