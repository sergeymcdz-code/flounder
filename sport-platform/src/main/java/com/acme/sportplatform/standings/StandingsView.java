package com.acme.sportplatform.standings;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only contract for documents and future exports. Public views omit athlete breakdowns. */
public record StandingsView(String title, String scope, List<ClubMedals> medals, List<ClubPoints> team,
        int unitsPending, int disciplinesIncluded, Instant calculatedAt, boolean preliminary) {
    public record ClubMedals(String club, int gold, int silver, int bronze) {}
    public record ClubPoints(String club, long points, int gold, int silver, int bronze,
            int scoredResults, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<Breakdown> breakdown) {}
    public record Breakdown(UUID disciplineId, String ageGroupCode, int place, int points, String athlete) {}
}
