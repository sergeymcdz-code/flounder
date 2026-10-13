package com.acme.sportplatform.publication.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ReadModels {
    private ReadModels() {}
    public record Event(UUID id, UUID sportId, String title, String description,
            OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
            OffsetDateTime eventStartAt, OffsetDateTime eventEndAt, String status,
            String slug, String venueName, String venueCity, String venueTimezone, String venueMeta) {}
    public record Session(UUID id, String name, int orderNo, OffsetDateTime startsAt,
            int breakBetweenEventsSec) {}
    public record LiveState(UUID currentUnitId, OffsetDateTime updatedAt) {}
    public record Discipline(UUID id, int number, String name, Integer distanceM,
            String stroke, String gender, UUID sessionId, Integer orderInSession,
            String ageGroups, String seedingMode) {}
    public record Unit(UUID id, UUID disciplineId, int number, String ageGroupCode,
            OffsetDateTime scheduledAt, int sequenceInSession, String status,
            OffsetDateTime actualStartedAt, OffsetDateTime scheduleShiftedAt) {}
    public record Entry(UUID unitId, UUID registrationId, UUID profileId, Integer lane,
            boolean withdrawn, Long seedTimeMs, String lastName, String firstName,
            int birthYear, String gender, String club, Long timeMs, String resultStatus,
            Integer place, Integer placeInAgeGroup) {}
}
