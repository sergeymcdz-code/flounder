package com.acme.sportplatform.results.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface RuleExecutionLogRepository extends JpaRepository<RuleExecutionLogEntity, UUID> {
    List<RuleExecutionLogEntity> findByEventDisciplineId(UUID id);
}
