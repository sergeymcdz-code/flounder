package com.acme.sportplatform.results.application;

import com.acme.sportplatform.events.SessionHeatsLookup;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionHeatsLookupService implements SessionHeatsLookup {
    private final CompetitionUnitRepository units;

    public SessionHeatsLookupService(CompetitionUnitRepository units) {
        this.units = units;
    }

    public Summary summary(UUID sessionId) {
        var s = units.sessionSummary(sessionId);
        return new Summary(
                s.getHeatsCount(),
                s.getEstimatedEndsAt() == null
                        ? null
                        : s.getEstimatedEndsAt().atOffset(java.time.ZoneOffset.UTC));
    }
}
