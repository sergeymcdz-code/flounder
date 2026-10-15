package com.acme.sportplatform.registrations.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface RegistrationRepository
        extends JpaRepository<RegistrationEntity, UUID> {
    @Query("select r.eventId from RegistrationEntity r where r.id=:id")
    Optional<UUID> findEventIdById(UUID id);

    List<RegistrationEntity> findByEventIdInAndPaymentStatus(java.util.Collection<UUID> eventIds, String paymentStatus);
    boolean existsByEventDisciplineId(UUID id);
    boolean existsByEventIdAndParticipantProfileId(UUID eventId, UUID participantProfileId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationEntity r where r.id=:id")
    Optional<RegistrationEntity> lockById(UUID id);
    List<RegistrationEntity> findByParticipantProfileId(UUID profileId);
    boolean existsByEventDisciplineIdAndParticipantProfileId(UUID disciplineId,UUID profileId);

        List<RegistrationEntity> findByEventId(UUID eventId);

        List<RegistrationEntity> findByEventIdIn(java.util.Collection<UUID> eventIds);

        List<RegistrationEntity> findByEventIdInAndStatus(java.util.Collection<UUID> eventIds, String status);

        Optional<RegistrationEntity> findByEventDisciplineIdAndParticipantProfileIdAndStatusNot(
                UUID eventDisciplineId, UUID participantProfileId, String status);

        List<RegistrationEntity> findByEventDisciplineId(UUID eventDisciplineId);

        List<RegistrationEntity> findByParticipantUserId(UUID participantUserId);
        
        List<RegistrationEntity> findByStatus(String status);

        List<RegistrationEntity> findByEventIdAndStatus(UUID eventId, String status);
        Optional<RegistrationEntity> findByEventDisciplineIdAndParticipantUserId(
                UUID eventDisciplineId,
                UUID participantUserId
        );

        boolean existsByEventDisciplineIdAndParticipantUserId(
                UUID eventDisciplineId,
                UUID participantUserId
        );

        long countByEventDisciplineIdAndStatusIn(
                UUID eventDisciplineId,
                List<String> statuses
        );

        List<RegistrationEntity> findByIdIn(List<UUID> ids);
}