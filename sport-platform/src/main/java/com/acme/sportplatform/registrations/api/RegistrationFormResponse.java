package com.acme.sportplatform.registrations.api;

import com.acme.sportplatform.competition.AgeGroup;

import java.math.BigDecimal;
import java.util.*;

public record RegistrationFormResponse(List<Discipline> disciplines, boolean requiresSeedTime,
        boolean registrationOpen, Integer maxParticipants, long participantsCount) {
    public RegistrationFormResponse(List<Discipline> disciplines, boolean requiresSeedTime) {
        this(disciplines, requiresSeedTime, true, null, 0);
    }
    public record Discipline(
            UUID id,
            String name,
            Integer distanceM,
            String stroke,
            String gender,
            List<AgeGroup> ageGroups,
            BigDecimal entryFee, Integer maxParticipants, long participantsCount,
            Map<String, Integer> limitsByAgeGroup, Map<String, Long> participantsByAgeGroup) {
        public Discipline(UUID id, String name, Integer distanceM, String stroke, String gender,
                List<AgeGroup> ageGroups, BigDecimal entryFee) {
            this(id, name, distanceM, stroke, gender, ageGroups, entryFee, null, 0, null, Map.of());
        }
    }
}
