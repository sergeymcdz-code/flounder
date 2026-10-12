package com.acme.sportplatform.sports.infrastructure.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DisciplineTemplateRepository extends JpaRepository<DisciplineTemplateEntity, UUID> {
    List<DisciplineTemplateEntity> findBySportId(UUID sportId);
    Optional<DisciplineTemplateEntity> findBySportIdAndCode(UUID sportId, String code);
    boolean existsBySportIdAndCode(UUID sportId, String code);
}