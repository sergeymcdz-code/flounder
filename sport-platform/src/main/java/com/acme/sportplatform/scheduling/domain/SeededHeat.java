package com.acme.sportplatform.scheduling.domain;

import java.util.List;

public record SeededHeat(int number, String ageGroupCode, List<SeededLane> lanes) {
    public SeededHeat {
        lanes = List.copyOf(lanes);
    }
}
