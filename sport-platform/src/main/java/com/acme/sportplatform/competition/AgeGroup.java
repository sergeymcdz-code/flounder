package com.acme.sportplatform.competition;

public record AgeGroup(
        String code, String name, String gender, Integer birthYearFrom, Integer birthYearTo) {
    public boolean includes(String athleteGender, int birthYear) {
        return ("X".equals(gender) || gender.equals(athleteGender))
                && (birthYearFrom == null || birthYear >= birthYearFrom)
                && (birthYearTo == null || birthYear <= birthYearTo);
    }
}
