package com.acme.sportplatform.registrations.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository
        extends JpaRepository<RegistrationEntity, UUID> {

        List<RegistrationEntity> findByEventId(UUID eventId);

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