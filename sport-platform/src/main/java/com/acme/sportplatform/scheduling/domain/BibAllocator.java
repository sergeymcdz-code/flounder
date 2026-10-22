package com.acme.sportplatform.scheduling.domain;

import com.acme.sportplatform.common.exception.BusinessException;
import java.time.LocalDate;
import java.util.*;

public final class BibAllocator {
    public enum Mode { BY_CLUB_THEN_NAME, BY_NAME }
    public record Athlete(UUID id, String lastName, String firstName, LocalDate birthDate, String club) {}
    private BibAllocator() {}
    public static Map<UUID, Integer> assign(List<Athlete> athletes, Map<UUID, Integer> existing, Mode mode, int startFrom) {
        if (startFrom < 1) throw new BusinessException("scheduling.invalid_bib", "Номер должен быть положительным");
        Comparator<String> text = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        Comparator<Athlete> order = Comparator.comparing(Athlete::lastName, text).thenComparing(Athlete::firstName, text)
                .thenComparing(Athlete::birthDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(a -> a.id().toString());
        if (mode == Mode.BY_CLUB_THEN_NAME) order = Comparator.comparing(Athlete::club, text).thenComparing(order);
        var result = new LinkedHashMap<>(existing);
        var used = new HashSet<>(existing.values());
        long next = startFrom;
        for (var athlete : athletes.stream().distinct().sorted(order).toList()) {
            if (result.containsKey(athlete.id())) continue;
            while (next <= Integer.MAX_VALUE && used.contains((int) next)) next++;
            if (next > Integer.MAX_VALUE) throw new BusinessException("scheduling.invalid_bib", "Диапазон стартовых номеров исчерпан");
            result.put(athlete.id(), (int) next); used.add((int) next++);
        }
        return result;
    }
}
