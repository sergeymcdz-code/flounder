package com.acme.sportplatform.events.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record EventSessionResponse(
        UUID id,
        UUID eventId,
        String name,
        int orderNo,
        OffsetDateTime startsAt,
        int breakBetweenEventsSec,
        long heatsCount,
        OffsetDateTime estimatedEndsAt) {}
