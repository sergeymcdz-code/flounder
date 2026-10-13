package com.acme.sportplatform.scheduling.domain;

import java.time.Instant;
import java.util.*;

public final class ScheduleBuilder {
    public List<ScheduledHeat> build(
            List<SessionSpec> sessions,
            List<DisciplineSpec> disciplines,
            Map<UUID, List<SeededHeat>> heats,
            SchedulingRules rules) {
        List<ScheduledHeat> out = new ArrayList<>();
        for (SessionSpec session :
                sessions.stream().sorted(Comparator.comparingInt(SessionSpec::orderNo)).toList()) {
            Instant time = session.startsAt();
            int seq = 1;
            boolean first = true;
            for (DisciplineSpec discipline :
                    disciplines.stream()
                            .filter(d -> session.id().equals(d.sessionId()))
                            .sorted(
                                    Comparator.comparingInt(DisciplineSpec::orderInSession)
                                            .thenComparingInt(DisciplineSpec::number))
                            .toList()) {
                List<SeededHeat> list =
                        heats.getOrDefault(discipline.id(), List.of()).stream()
                                .sorted(Comparator.comparingInt(SeededHeat::number))
                                .toList();
                if (list.isEmpty()) continue;
                if (!first)
                    time =
                            time.plusSeconds(
                                    session.breakBetweenEventsSec() == null
                                            ? rules.breakBetweenEventsSec()
                                            : session.breakBetweenEventsSec());
                first = false;
                for (SeededHeat heat : list) {
                    out.add(
                            new ScheduledHeat(
                                    discipline.id(),
                                    heat.number(),
                                    time,
                                    seq++,
                                    heat.lanes().stream()
                                            .map(SeededLane::athleteId)
                                            .filter(Objects::nonNull)
                                            .toList()));
                    time = time.plusSeconds(rules.duration(discipline));
                }
            }
        }
        return List.copyOf(out);
    }
}
