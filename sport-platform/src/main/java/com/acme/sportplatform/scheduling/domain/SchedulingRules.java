package com.acme.sportplatform.scheduling.domain;

import java.util.Map;

public record SchedulingRules(
        Map<Integer, Integer> heatDurationSecByDistance,
        int defaultHeatDurationSec,
        int breakBetweenEventsSec,
        int minRestBetweenStartsSec) {
    public SchedulingRules {
        heatDurationSecByDistance = Map.copyOf(heatDurationSecByDistance);
    }

    public static SchedulingRules defaults() {
        return new SchedulingRules(
                Map.of(25, 60, 50, 90, 100, 150, 200, 270, 400, 480, 800, 840, 1500, 1500),
                180,
                60,
                900);
    }

    public int duration(DisciplineSpec discipline) {
        return discipline.heatDurationSec() != null
                ? discipline.heatDurationSec()
                : heatDurationSecByDistance.getOrDefault(
                        discipline.distanceM(), defaultHeatDurationSec);
    }
}
