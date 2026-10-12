package com.acme.sportplatform.results.api;

import java.util.UUID;

public record AssignRegistrationRequest(
        UUID registrationId,
        Integer laneOrPosition
) {}