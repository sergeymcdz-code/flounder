package com.acme.sportplatform.results;

import java.util.UUID;

public record ResultsPublishedEvent(UUID publicationId, UUID disciplineId) {}
