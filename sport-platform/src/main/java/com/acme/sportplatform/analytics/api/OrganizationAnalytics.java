package com.acme.sportplatform.analytics.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrganizationAnalytics(UUID organizationId, String name, OffsetDateTime generatedAt,
        long events, List<EventAnalytics.Count> byStatus, long participants, long uniqueParticipants,
        long repeatParticipants, double averageEventSize, List<Revenue> revenue) {
    public record Revenue(String currency, BigDecimal amount) {}
}
