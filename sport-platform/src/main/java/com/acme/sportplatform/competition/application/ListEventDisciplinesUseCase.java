package com.acme.sportplatform.competition.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.competition.api.EventDisciplineResponse;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;

@Service
@Transactional(readOnly = true)
public class ListEventDisciplinesUseCase {

    private final EventDisciplineRepository eventDisciplineRepository;
    private final EventDisciplineMapper eventDisciplineMapper;

    public ListEventDisciplinesUseCase(
            EventDisciplineRepository eventDisciplineRepository,
            EventDisciplineMapper eventDisciplineMapper
    ) {
        this.eventDisciplineRepository = eventDisciplineRepository;
        this.eventDisciplineMapper = eventDisciplineMapper;
    }

    public List<EventDisciplineResponse> execute(UUID eventId) {
        return eventDisciplineRepository.findByEventId(eventId).stream()
                .map(eventDisciplineMapper::toResponse)
                .toList();
    }
}