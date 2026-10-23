package com.acme.sportplatform.publication.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Public read contracts from the demo specification, section 5.7. */
public final class PublicResponses {
    private PublicResponses() {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Event(UUID id, UUID sportId, String title, String description,
            OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
            OffsetDateTime eventStartAt, OffsetDateTime eventEndAt, String status,
            String publicSlug, boolean registrationOpen, Venue venue,
            List<Session> sessions, List<Discipline> disciplines, Integer maxParticipants, long participantsCount, Organization organization, @JsonInclude(JsonInclude.Include.NON_NULL) com.acme.sportplatform.events.EventPaymentSettings payment) {
        public Event(UUID id, UUID sportId, String title, String description,
                OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
                OffsetDateTime eventStartAt, OffsetDateTime eventEndAt, String status,
                String publicSlug, boolean registrationOpen, Venue venue, List<Session> sessions,
                List<Discipline> disciplines, Integer maxParticipants, long participantsCount) {
            this(id, sportId, title, description, registrationOpenAt, registrationCloseAt, eventStartAt,
                    eventEndAt, status, publicSlug, registrationOpen, venue, sessions, disciplines, maxParticipants, participantsCount, null, null);
        }
        public Event(UUID id, UUID sportId, String title, String description,
                OffsetDateTime registrationOpenAt, OffsetDateTime registrationCloseAt,
                OffsetDateTime eventStartAt, OffsetDateTime eventEndAt, String status,
                String publicSlug, boolean registrationOpen, Venue venue,
                List<Session> sessions, List<Discipline> disciplines) {
            this(id, sportId, title, description, registrationOpenAt, registrationCloseAt, eventStartAt,
                    eventEndAt, status, publicSlug, registrationOpen, venue, sessions, disciplines, null, 0, null, null);
        }
    }
    public record Organization(String name, String publicSlug, boolean hasLogo) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Venue(String name, String city, Integer lanes, Integer courseLength, String timezone) {}
    public record Session(UUID id, String name, int orderNo, OffsetDateTime startsAt,
            int breakBetweenEventsSec) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Discipline(UUID id, int number, String name, Integer distanceM,
            String stroke, String gender, UUID sessionId, Integer orderInSession,
            boolean unitsPublished, java.math.BigDecimal entryFeeAmount, String entryFeeCurrency) {
        public Discipline(UUID id,int number,String name,Integer distanceM,String stroke,String gender,UUID sessionId,Integer orderInSession,boolean unitsPublished) {
            this(id,number,name,distanceM,stroke,gender,sessionId,orderInSession,unitsPublished,null,null);
        }
    }
    public record StartList(List<StartSession> sessions) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StartSession(String id, String name, OffsetDateTime startsAt,
            List<StartDiscipline> disciplines) {}
    public record StartDiscipline(UUID id, int number, String name, List<Heat> heats) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Heat(UUID id, int number, String ageGroupCode, OffsetDateTime scheduledAt,
            int sequenceInSession, String status, List<Lane> lanes) {}
    @io.swagger.v3.oas.annotations.media.Schema(oneOf = {EmptyLane.class, OccupiedLane.class})
    public sealed interface Lane permits EmptyLane, OccupiedLane {}
    public record EmptyLane(int lane, boolean empty) implements Lane {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OccupiedLane(int lane, Boolean withdrawn, UUID registrationId,
            Athlete athlete, @JsonInclude(JsonInclude.Include.ALWAYS) Long seedTimeMs,
            @JsonInclude(JsonInclude.Include.ALWAYS) String display) implements Lane {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Athlete(String lastName, String firstName, int birthYear, String club) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Schedule(String sessionName, String disciplineName, int heatNumber,
            int sequenceInSession, OffsetDateTime scheduledAt, String status, OffsetDateTime scheduleShiftedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record LiveUnit(UUID id, int number, String ageGroupCode, OffsetDateTime scheduledAt,
            int sequenceInSession, String status, List<Lane> lanes, UUID disciplineId,
            int disciplineNumber, String disciplineName, OffsetDateTime actualStartedAt, OffsetDateTime estimatedStartAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Live(LiveUnit currentUnit, List<LiveUnit> next, OffsetDateTime updatedAt, long delaySec) {}
    public record Results(List<DisciplineResults> disciplines) {}
    public record DisciplineResults(UUID id, int number, String name,
            List<ResultRow> overall, List<AgeGroupResults> byAgeGroup) {}
    public record AgeGroupResults(String code, String name, List<ResultRow> rows) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ResultRow(Integer place, String lastName, String firstName,
            int birthYear, String club, String display, Long timeMs, String status) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AthleteSwims(UUID athleteKey, String lastName, String firstName,
            int birthYear, String club, List<Swim> swims) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Swim(String disciplineName, int heatNumber, Integer lane,
            OffsetDateTime scheduledAt, SwimResult result) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SwimResult(String display, String status, Integer place, Integer placeInAgeGroup) {}
}
