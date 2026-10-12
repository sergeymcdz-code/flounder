package com.acme.sportplatform.results.infrastructure.jpa;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface EventLiveStateRepository extends JpaRepository<EventLiveStateEntity, UUID> {
    @Modifying
    @Query(
            value =
                    "insert into event_live_state(event_id,updated_at) values(:eventId,now()) on"
                        + " conflict(event_id) do nothing",
            nativeQuery = true)
    void ensureEvent(UUID eventId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EventLiveStateEntity s where s.eventId=:eventId")
    Optional<EventLiveStateEntity> lockForEvent(UUID eventId);
}
