package com.acme.sportplatform.standings.domain;

import java.util.List;
import java.util.UUID;

public record StandingsConfig(boolean enabled, Scope scope, List<Integer> pointsByPlace,
        Integer maxScoredPerClubPerDiscipline, List<UUID> disciplineIds, boolean published, String title) {
    public enum Scope { AGE_GROUP, ABSOLUTE }
    public StandingsConfig {
        pointsByPlace = List.copyOf(pointsByPlace);
        disciplineIds = disciplineIds == null ? null : List.copyOf(disciplineIds);
    }
    public static StandingsConfig defaults() {
        return new StandingsConfig(false, Scope.AGE_GROUP, List.of(9,7,6,5,4,3,2,1), null, null, false, null);
    }
}
