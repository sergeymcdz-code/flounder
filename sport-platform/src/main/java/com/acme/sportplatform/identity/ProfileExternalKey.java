package com.acme.sportplatform.identity;

import com.acme.sportplatform.common.exception.BusinessException;

import java.time.LocalDate;
import java.util.Locale;

public final class ProfileExternalKey {
    private ProfileExternalKey() {}

    public static String build(
            String lastName, String firstName, LocalDate birthDate, String gender) {
        if (birthDate == null || lastName == null || firstName == null || gender == null)
            throw invalid();
        return normalize(lastName + "|" + firstName + "|" + birthDate + "|" + gender);
    }

    public static String normalize(String key) {
        if (key == null) throw invalid();
        String[] parts = key.split("\\|", -1);
        if (parts.length != 4) throw invalid();
        for (int i = 0; i < parts.length; i++) parts[i] = part(parts[i]);
        if (parts[0].isEmpty() || parts[1].isEmpty()) throw invalid();
        try {
            parts[2] = LocalDate.parse(parts[2]).toString();
        } catch (Exception e) {
            throw invalid();
        }
        parts[3] = gender(parts[3]);
        String result = String.join("|", parts);
        if (result.length() > 255) throw invalid();
        return result;
    }

    public static String gender(String value) {
        return switch (part(value)) {
            case "m", "male" -> "m";
            case "f", "female" -> "f";
            case "x", "other" -> "x";
            default -> throw invalid();
        };
    }

    private static String part(String s) {
        if (s == null) throw invalid();
        return s.replaceAll("[\\s\\p{Z}]+", " ").strip().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static BusinessException invalid() {
        return new BusinessException(
                "identity.invalid_external_key", "Некорректный внешний ключ профиля");
    }
}
