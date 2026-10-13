package com.acme.sportplatform.results.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventAccessService;
import com.acme.sportplatform.identity.AuthenticatedUserPrincipal;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;

@Service
@Transactional(readOnly = true)
public class ResultsAccessService {
    private final EventDisciplineLookup eventDisciplineLookup;
    private final EventAccessService eventAccessService;
    private final CompetitionUnitRepository competitionUnitRepository;
    private final CompetitionUnitEntryRepository entryRepository;

    public ResultsAccessService(EventDisciplineLookup eventDisciplineLookup, EventAccessService eventAccessService,
            CompetitionUnitRepository competitionUnitRepository, CompetitionUnitEntryRepository entryRepository) {
        this.eventDisciplineLookup = eventDisciplineLookup;
        this.eventAccessService = eventAccessService;
        this.competitionUnitRepository = competitionUnitRepository;
        this.entryRepository = entryRepository;
    }

    public void assertDisciplineAccess(UUID id, AuthenticatedUserPrincipal principal) {
        var discipline = eventDisciplineLookup.findById(id).orElseThrow(() ->
                new BusinessException("competition.discipline_not_found", "Дисциплина не найдена"));
        eventAccessService.assertCanManage(discipline.eventId(), principal);
    }

    public void assertUnitAccess(UUID id, AuthenticatedUserPrincipal principal) {
        var unit = competitionUnitRepository.findById(id).orElseThrow(() ->
                new BusinessException("results.competition_unit_not_found", "Заплыв не найден"));
        assertDisciplineAccess(unit.getEventDisciplineId(), principal);
    }

    public void assertEntryAccess(UUID id, AuthenticatedUserPrincipal principal) {
        var entry = entryRepository.findById(id).orElseThrow(() ->
                new BusinessException("results.entry_not_found", "Участник заплыва не найден"));
        assertUnitAccess(entry.getCompetitionUnitId(), principal);
    }

}
