package com.acme.sportplatform.registrations.infrastructure.jpa;

import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ParticipantDocumentRepository extends JpaRepository<ParticipantDocumentEntity, UUID> {
    List<ParticipantDocumentEntity> findByProfileIdOrderByUploadedAtDesc(UUID profileId);
    @Query("select d from ParticipantDocumentEntity d where d.profileId in :profileIds order by d.uploadedAt desc, d.id desc")
    List<ParticipantDocumentEntity> findForProfiles(Collection<UUID> profileIds);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ParticipantDocumentEntity d where d.id=:id")
    Optional<ParticipantDocumentEntity> lockById(UUID id);
}
