package com.acme.sportplatform.results.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompetitionUnitRepository
        extends JpaRepository<CompetitionUnitEntity, UUID> {
    boolean existsByEventDisciplineId(UUID id);

    interface SessionSummary {
        long getHeatsCount();

        java.time.Instant getEstimatedEndsAt();
    }

    @org.springframework.data.jpa.repository.Query(
            value =
                    "select count(u.id) as heatsCount, max(u.scheduled_at +"
                        + " coalesce(u.heat_duration_sec,d.heat_duration_sec) * interval '1"
                        + " second') as estimatedEndsAt from competition_units u join"
                        + " event_disciplines d on d.id=u.event_discipline_id where"
                        + " d.session_id=:sessionId",
            nativeQuery = true)
    SessionSummary sessionSummary(UUID sessionId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select u from CompetitionUnitEntity u where u.id=:id")
    java.util.Optional<CompetitionUnitEntity> lockById(UUID id);

    @org.springframework.data.jpa.repository.Query(
            value =
                    "select u.* from competition_units u join event_disciplines d on"
                        + " d.id=u.event_discipline_id where d.event_id=:eventId and u.status in"
                        + " ('DRAFT','SCHEDULED','PUBLISHED') and (u.scheduled_at >"
                        + " cast(:scheduledAt as timestamptz) or (u.scheduled_at ="
                        + " cast(:scheduledAt as timestamptz) and (u.sequence_number > :sequence or"
                        + " (u.sequence_number=:sequence and u.id > :id))) or (u.scheduled_at is"
                        + " null and (cast(:scheduledAt as timestamptz) is not null or"
                        + " u.sequence_number > :sequence or (u.sequence_number=:sequence and u.id"
                        + " > :id)))) order by u.scheduled_at asc nulls last,u.sequence_number,u.id"
                        + " limit 1",
            nativeQuery = true)
    java.util.Optional<CompetitionUnitEntity> nextScheduled(
            UUID eventId, java.time.OffsetDateTime scheduledAt, int sequence,UUID id);

    List<CompetitionUnitEntity>
    findByEventDisciplineIdOrderBySequenceNumberAsc(
            UUID eventDisciplineId
    );

    List<CompetitionUnitEntity>
    findByEventDisciplineIdAndStatusOrderBySequenceNumberAsc(
            UUID eventDisciplineId,
            String status
    );
}
