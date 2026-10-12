package com.acme.sportplatform.sports.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SportRepository extends JpaRepository<SportEntity, UUID> {
    Optional<SportEntity> findByCode(String code);
    boolean existsByCode(String code);
}