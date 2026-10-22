package com.acme.sportplatform.standings.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record StandingsConfigRequest(boolean enabled, String scope, JsonNode pointsByPlace,
        Integer maxScoredPerClubPerDiscipline, List<UUID> disciplineIds, boolean published, String title) {}
