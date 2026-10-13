package com.acme.sportplatform.results.api;

import java.util.*;

public record DisciplineResultsResponse(UUID eventDisciplineId, List<AgeGroupResults> groups) {
    public record AgeGroupResults(
            String ageGroupCode, List<CompetitionUnitDetailsResponse.EntryView> entries) {}
}
