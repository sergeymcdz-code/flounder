package com.acme.sportplatform.scheduling.application;

import com.acme.sportplatform.scheduling.api.EventGenerationResponse;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GenerateForEventUseCase {
    private final SchedulingData data;
    private final GenerateCompetitionUnitsUseCase generate;

    public GenerateForEventUseCase(SchedulingData data, GenerateCompetitionUnitsUseCase generate) {
        this.data = data;
        this.generate = generate;
    }

    @Transactional
    public EventGenerationResponse execute(
            UUID eventId, boolean force, boolean circleSeedTopHeats, Authentication principal) {
        data.access.assertCanManage(eventId, principal);
        data.events.lockForScheduling(eventId);
        long start = System.nanoTime();
        var out =
                data.disciplines(eventId).stream()
                        .map(
                                d ->
                                        new EventGenerationResponse.Discipline(
                                                d.id(),
                                                generate.execute(
                                                        d.id(),
                                                        force,
                                                        circleSeedTopHeats,
                                                        principal)))
                        .toList();
        return new EventGenerationResponse(out, (System.nanoTime() - start) / 1_000_000);
    }
}
