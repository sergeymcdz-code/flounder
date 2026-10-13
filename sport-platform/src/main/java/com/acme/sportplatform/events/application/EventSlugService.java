package com.acme.sportplatform.events.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;

@Service
public class EventSlugService {
    private static final String CYRILLIC = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String[] LATIN = {"a", "b", "v", "g", "d", "e", "yo", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f", "kh", "ts", "ch", "sh", "shch", "", "y", "", "e", "yu", "ya"};
    private final EventRepository events;

    public EventSlugService(EventRepository events) { this.events = events; }

    public String generate(String title) {
        var transliterated = new StringBuilder();
        for (char c : (title == null ? "" : title).toLowerCase(Locale.ROOT).toCharArray()) {
            int index = CYRILLIC.indexOf(c);
            transliterated.append(index < 0 ? String.valueOf(c) : LATIN[index]);
        }
        String base = transliterated.toString().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "event";
        if (base.length() > 250) base = base.substring(0, 250).replaceAll("-+$", "");
        String slug = base;
        while (events.existsByPublicSlug(slug)) slug = base + "-" + UUID.randomUUID().toString().substring(0, 4);
        return slug;
    }
}
