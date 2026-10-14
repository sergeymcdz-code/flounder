package com.acme.sportplatform.notifications.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NotificationLogPage(List<Item> items, int page, long totalElements, int totalPages) {
    public record Item(UUID id, String recipientEmail, String templateCode, String status,
                       int attempts, String lastError, OffsetDateTime createdAt, OffsetDateTime sentAt) {}
}
