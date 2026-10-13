package com.acme.sportplatform.scheduling.domain;

import java.util.UUID;

public record SeedEntry(
        UUID registrationId,
        UUID athleteId,
        String lastName,
        String firstName,
        Integer birthYear,
        String gender,
        String club,
        Long seedTimeMs) {
    public String athleteLabel() {
        String name =
                (java.util.Objects.toString(lastName, "")
                                + " "
                                + java.util.Objects.toString(firstName, ""))
                        .trim();
        return (name.isEmpty() ? "Спортсмен" : name)
                + " ("
                + (birthYear == null ? "год неизвестен" : birthYear)
                + ")";
    }
}
