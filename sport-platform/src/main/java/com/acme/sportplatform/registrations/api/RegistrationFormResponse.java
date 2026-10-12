package com.acme.sportplatform.registrations.api;

import com.acme.sportplatform.competition.AgeGroup;

import java.math.BigDecimal;
import java.util.*;

public record RegistrationFormResponse(List<Discipline> disciplines, boolean requiresSeedTime) {
    public record Discipline(
            UUID id,
            String name,
            Integer distanceM,
            String stroke,
            String gender,
            List<AgeGroup> ageGroups,
            BigDecimal entryFee) {}
}
