package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.registrations.RegistrationWithdrawn;
import com.acme.sportplatform.results.CompetitionUnitStore;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class RegistrationWithdrawalListener {
    private final CompetitionUnitStore units;
    private final EventDisciplineLookup disciplines;
    private final EventLookup events;

    public RegistrationWithdrawalListener(
            CompetitionUnitStore units, EventDisciplineLookup disciplines, EventLookup events) {
        this.units = units;
        this.disciplines = disciplines;
        this.events = events;
    }

    @ApplicationModuleListener
    public void on(RegistrationWithdrawn event) {
        events.lockForScheduling(disciplines.getForScheduling(event.eventDisciplineId()).eventId());
        units.withdraw(event.registrationId());
    }
}
