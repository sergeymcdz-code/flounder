package com.acme.sportplatform.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventDisciplineLookup {

    EventDisciplineLookupResult lockPublishedById(UUID eventDisciplineId);

    EventDisciplineLookupResult getPublishedById(UUID eventDisciplineId);

    EventDisciplineLookupResult getForScheduling(UUID eventDisciplineId);

    List<EventDisciplineLookupResult> findForSchedulingByEventId(UUID eventId);

    List<EventDisciplineSummary> findByEventId(UUID eventId);

    List<EventDisciplineSummary> findPublishedByEventId(UUID eventId);

    Optional<EventDisciplineSummary> findById(UUID eventDisciplineId);
}