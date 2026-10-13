package com.acme.sportplatform.scheduling.api;

import com.acme.sportplatform.scheduling.domain.Violation;

import java.time.Instant;
import java.util.*;

public record GenerationResponse(
        UUID runId,
        List<Heat> heats,
        List<Violation> violations,
        List<UUID> excludedRegistrationIds,
        long durationMs) {
    public record Athlete(String lastName, String firstName, Integer birthYear, String club) {}

    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Lane(int lane, UUID registrationId, Athlete athlete, Long seedTimeMs) {}

    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Heat(
            UUID id, int number, String ageGroupCode, Instant scheduledAt, List<Lane> lanes) {}
}
