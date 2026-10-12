package com.acme.sportplatform.publication.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventLiveStateRepository extends JpaRepository<EventLiveStateEntity, UUID> {}
