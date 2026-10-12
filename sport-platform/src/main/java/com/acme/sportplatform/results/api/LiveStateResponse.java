package com.acme.sportplatform.results.api;

import java.time.OffsetDateTime;
import java.util.UUID;

@com.fasterxml.jackson.annotation.JsonInclude(
        com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record LiveStateResponse(UUID eventId, UUID currentUnitId, OffsetDateTime updatedAt) {}
