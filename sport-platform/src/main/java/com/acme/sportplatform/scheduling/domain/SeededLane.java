package com.acme.sportplatform.scheduling.domain;

import java.util.UUID;

public record SeededLane(int lane, UUID registrationId, UUID athleteId, Long seedTimeMs) {}
