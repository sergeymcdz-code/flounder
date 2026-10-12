package com.acme.sportplatform.results.api;

import java.util.UUID;

public record RecordResultRequest(
        UUID competitionUnitEntryId,
        String rawValue,
        String status
) {}