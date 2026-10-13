package com.acme.sportplatform.scheduling.domain;

import java.time.Instant;
import java.util.UUID;

public record SessionSpec(UUID id, int orderNo, Instant startsAt, Integer breakBetweenEventsSec) {}
