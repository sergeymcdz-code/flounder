package com.acme.sportplatform.scheduling.domain;


public record AgeGroupSpec(
        String code, String name, String gender, Integer birthYearFrom, Integer birthYearTo) {
    public boolean contains(SeedEntry entry) {
        return entry.birthYear() != null
                && ("X".equals(gender) || gender.equals(entry.gender()))
                && (birthYearFrom == null || entry.birthYear() >= birthYearFrom)
                && (birthYearTo == null || entry.birthYear() <= birthYearTo);
    }
}
