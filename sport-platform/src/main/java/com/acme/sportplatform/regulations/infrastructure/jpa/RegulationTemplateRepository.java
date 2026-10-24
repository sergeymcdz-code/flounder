package com.acme.sportplatform.regulations.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulationTemplateRepository extends JpaRepository<RegulationTemplateEntity, UUID> {
    @org.springframework.data.jpa.repository.Query("select t from RegulationTemplateEntity t where :admin = true or t.organizationId is null or t.organizationId in :organizations order by t.name, t.id")
    java.util.List<RegulationTemplateEntity> findVisible(boolean admin, java.util.Collection<UUID> organizations);
    boolean existsBySportIdAndCodeAndOrganizationId(UUID sportId, String code, UUID organizationId);
    boolean existsBySportIdAndCode(UUID sportId, String code);
    Optional<RegulationTemplateEntity> findBySportIdAndCode(UUID sportId, String code);
}