package com.acme.sportplatform.results.api;

import java.util.UUID;

public record ResultResponse(
        UUID id,
        UUID competitionUnitEntryId,
        String rawValue,
        String resultType,
        String status,
        Integer finalPlace
) {}