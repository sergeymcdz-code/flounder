package com.acme.sportplatform.scheduling.domain;

import java.util.UUID;

public record Violation(
        String code,
        Severity severity,
        String message,
        UUID eventDisciplineId,
        Integer heatNumber,
        UUID athleteId,
        UUID registrationId) {
    public enum Severity {
        ERROR,
        WARNING
    }
}
