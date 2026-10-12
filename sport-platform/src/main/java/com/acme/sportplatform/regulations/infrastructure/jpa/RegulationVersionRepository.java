package com.acme.sportplatform.regulations.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulationVersionRepository extends JpaRepository<RegulationVersionEntity, UUID> {
    boolean existsByRegulationTemplateIdAndVersionNo(UUID regulationTemplateId, int versionNo);
    Optional<RegulationVersionEntity> findByRegulationTemplateIdAndVersionNo(UUID regulationTemplateId, int versionNo);
}