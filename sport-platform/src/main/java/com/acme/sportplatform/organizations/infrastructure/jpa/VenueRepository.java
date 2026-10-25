package com.acme.sportplatform.organizations.infrastructure.jpa;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<VenueEntity, UUID> {
    @org.springframework.data.jpa.repository.Query("select v from VenueEntity v where :admin = true or v.organizationId is null or v.organizationId in :organizations order by v.name, v.id")
    java.util.List<VenueEntity> findVisible(boolean admin, java.util.Collection<UUID> organizations);
}