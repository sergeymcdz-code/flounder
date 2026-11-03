package com.acme.sportplatform.results.api;

public record PublishCompetitionUnitRequest(
        boolean published,
        Boolean official
) {
    public PublishCompetitionUnitRequest(boolean published) {
        this(published, null);
    }
}