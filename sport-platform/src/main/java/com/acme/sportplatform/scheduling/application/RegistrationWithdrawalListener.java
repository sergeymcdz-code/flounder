package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.events.EventLockService;
import com.acme.sportplatform.registrations.RegistrationWithdrawn;
import com.acme.sportplatform.results.CompetitionUnitStore;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class RegistrationWithdrawalListener {
    private final CompetitionUnitStore units;
    private final EventLockService locks;

    public RegistrationWithdrawalListener(CompetitionUnitStore units, EventLockService locks) {
        this.units = units;
        this.locks = locks;
    }

    @ApplicationModuleListener
    public void on(RegistrationWithdrawn event) {
        locks.lockForUpdate(event.eventId());
        units.withdraw(event.registrationId());
    }
}
