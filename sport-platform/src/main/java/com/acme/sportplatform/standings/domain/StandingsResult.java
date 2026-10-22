package com.acme.sportplatform.standings.domain;

import com.acme.sportplatform.standings.StandingsView.ClubMedals;
import com.acme.sportplatform.standings.StandingsView.ClubPoints;
import java.time.Instant;
import java.util.List;

public record StandingsResult(List<ClubMedals> medals, List<ClubPoints> team,
        int unitsPending, int disciplinesIncluded, Instant calculatedAt) {
    public StandingsResult {
        medals = List.copyOf(medals);
        team = List.copyOf(team);
    }
    public boolean preliminary() { return unitsPending > 0; }
}
