package com.acme.sportplatform.identity;

import java.time.LocalDate;
import java.util.Locale;

public record ImportedProfileRequest(String lastName, String firstName, String middleName,
                                     LocalDate birthDate, String gender, String club) {
    public String externalKey() {
        return (normalize(lastName) + "|" + normalize(firstName) + "|" + birthDate + "|" + gender)
                .toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static String normalize(String value) {
        return value.replaceAll("(?U)\\s+", " ").strip();
    }
}
