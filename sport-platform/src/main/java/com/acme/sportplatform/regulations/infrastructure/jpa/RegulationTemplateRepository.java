package com.acme.sportplatform.regulations.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulationTemplateRepository extends JpaRepository<RegulationTemplateEntity, UUID> {
    boolean existsBySportIdAndCode(UUID sportId, String code);
    Optional<RegulationTemplateEntity> findBySportIdAndCode(UUID sportId, String code);
}