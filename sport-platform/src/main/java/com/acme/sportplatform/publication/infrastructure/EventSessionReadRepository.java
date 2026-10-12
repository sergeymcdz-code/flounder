package com.acme.sportplatform.publication.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface EventSessionReadRepository extends Repository<EventSessionReadEntity, UUID> {
    List<EventSessionReadEntity> findByEventIdOrderByOrderNo(UUID eventId);
}
