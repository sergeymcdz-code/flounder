package com.acme.sportplatform.analytics.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EventAnalytics(UUID eventId, String title, String timezone, OffsetDateTime generatedAt,
        Registrations registrations, List<Discipline> byDiscipline, List<Breakdown> byAgeGroup,
        List<Breakdown> byClub, List<Breakdown> byGender, List<Session> sessions,
        Results results, Validator validator, ImportStats importStats) {
    public record Count(String key, long count) {}
    public record Day(LocalDate date, long count) {}
    public record Funnel(long submitted, long confirmed, long paid, long admitted) {}
    public record Registrations(long total, long participants, List<Count> byStatus,
            List<Day> byDay, Funnel funnel) {}
    public record Breakdown(String key, String name, long registrations, long participants) {}
    public record Discipline(UUID id, String name, long registrations, long heats, long participants) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Session(UUID id, String name, long heats, double estimatedDurationMin,
            OffsetDateTime estimatedEndsAt, Long delaySec) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Results(long publishedHeats, Double medianPublicationSec, Double p95PublicationSec,
            long latencySamples, String latencySource, long corrected) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Validator(UUID runId, OffsetDateTime executedAt, long errors, long warnings) {}
    public record ImportStats(boolean available, String reason, List<ImportRun> recent) {}
    public record ImportRun(OffsetDateTime executedAt, long rows, long created, long updated,
            long errors, long durationMs) {}
}
