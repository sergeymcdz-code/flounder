package com.acme.sportplatform.common;

import com.acme.sportplatform.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.Locale;

public final class TimeParser {
    private TimeParser() {}

    public static long parse(String value) {
        try {
            if (value == null || !value.matches("\\d+(?::\\d{1,2}){0,2}(?:\\.\\d{1,3})?"))
                throw new IllegalArgumentException();
            String[] parts = value.split(":");
            BigDecimal seconds = new BigDecimal(parts[parts.length - 1]);
            if (parts.length > 1 && seconds.compareTo(BigDecimal.valueOf(60)) >= 0)
                throw new IllegalArgumentException();
            long minutes = parts.length > 1 ? Long.parseLong(parts[parts.length - 2]) : 0;
            if (parts.length == 3 && minutes >= 60) throw new IllegalArgumentException();
            long hours = parts.length == 3 ? Long.parseLong(parts[0]) : 0;
            long ms =
                    seconds.add(BigDecimal.valueOf(minutes).multiply(BigDecimal.valueOf(60)))
                            .add(BigDecimal.valueOf(hours).multiply(BigDecimal.valueOf(3600)))
                            .multiply(BigDecimal.valueOf(1000))
                            .longValueExact();
            if (ms <= 0) throw new IllegalArgumentException();
            return ms;
        } catch (IllegalArgumentException | ArithmeticException ex) {
            throw new BusinessException(
                    "common.invalid_time",
                    "Неверное время: используйте сс.сс, мм:сс.сс или ч:мм:сс.сс");
        }
    }

    public static String format(long ms) {
        return format(Long.valueOf(ms));
    }

    public static String format(Long ms) {
        if (ms == null) return null;
        if (ms < 0)
            throw new BusinessException("common.invalid_time", "Время не может быть отрицательным");
        return String.format(
                Locale.ROOT, "%02d:%02d.%02d", ms / 60000, ms / 1000 % 60, ms % 1000 / 10);
    }
}
