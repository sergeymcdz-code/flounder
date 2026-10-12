package com.acme.sportplatform.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventDisciplineLookup {

    EventDisciplineLookupResult getPublishedById(UUID eventDisciplineId);

    List<EventDisciplineSummary> findByEventId(UUID eventId);

    List<EventDisciplineSummary> findPublishedByEventId(UUID eventId);

    Optional<EventDisciplineSummary> findById(UUID eventDisciplineId);
}