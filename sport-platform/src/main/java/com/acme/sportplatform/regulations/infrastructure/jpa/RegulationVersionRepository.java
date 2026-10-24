package com.acme.sportplatform.regulations.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulationVersionRepository extends JpaRepository<RegulationVersionEntity, UUID> {
    @org.springframework.data.jpa.repository.Query("select v from RegulationVersionEntity v, RegulationTemplateEntity t where v.regulationTemplateId = t.id and (:admin = true or ((v.organizationId is null or v.organizationId in :organizations) and (t.organizationId is null or t.organizationId in :organizations))) order by v.createdAt, v.id")
    java.util.List<RegulationVersionEntity> findVisible(boolean admin, java.util.Collection<UUID> organizations);
    boolean existsByRegulationTemplateIdAndVersionNoAndOrganizationId(UUID regulationTemplateId, int versionNo, UUID organizationId);
    boolean existsByRegulationTemplateIdAndVersionNo(UUID regulationTemplateId, int versionNo);
    Optional<RegulationVersionEntity> findByRegulationTemplateIdAndVersionNo(UUID regulationTemplateId, int versionNo);
}