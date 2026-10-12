package com.acme.sportplatform.competition.application;

import com.acme.sportplatform.competition.ImportDisciplineLookup;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ImportDisciplineLookupService implements ImportDisciplineLookup {
    private final EventDisciplineRepository disciplines;

    public ImportDisciplineLookupService(EventDisciplineRepository disciplines) {
        this.disciplines = disciplines;
    }

    @Override
    public List<Discipline> findByEventId(UUID eventId) {
        return disciplines.findByEventId(eventId).stream().map(d -> new Discipline(d.getId(), d.getCode(),
                d.getName(), d.getStatus(), d.getGender(), d.getAgeGroupsJson(), d.getEntryFeeAmount())).toList();
    }
}
