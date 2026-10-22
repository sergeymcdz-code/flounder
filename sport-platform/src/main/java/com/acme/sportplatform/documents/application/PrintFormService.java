package com.acme.sportplatform.documents.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.documents.api.PdfDocument;
import com.acme.sportplatform.documents.domain.Protocol;
import com.acme.sportplatform.documents.domain.Protocol.Discipline;
import com.acme.sportplatform.documents.domain.Protocol.Heat;
import com.acme.sportplatform.documents.domain.Protocol.ResultRow;
import com.acme.sportplatform.documents.domain.Protocol.ResultSection;
import com.acme.sportplatform.documents.infrastructure.PdfRenderer;
import com.acme.sportplatform.events.DiplomaSettings;
import com.acme.sportplatform.events.DiplomaSettingsLookup;
import com.acme.sportplatform.events.EventLockService;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.files.FileLookup;
import com.acme.sportplatform.organizations.VenueLookup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

@Service
public class PrintFormService {
    private static final String LOGO = logo();

    private static String logo() {
        try (var input = PrintFormService.class.getResourceAsStream("/brand/sportgate-192.png")) {
            if (input == null) throw new IllegalStateException("Missing SportGate logo");
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(input.readAllBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read SportGate logo", ex);
        }
    }

    public enum Scope {
        AGE_GROUP,
        ABSOLUTE
    }

    public enum CardFormat {
        A5,
        A4x2
    }

    public record Card(Discipline discipline, Heat heat, int total) {}

    public record Diploma(String title, String body, String footer, boolean showLogo) {}

    private final DocumentDataService data;
    private final PdfRenderer renderer;
    private final EventLookup events;
    private final DiplomaSettingsLookup settings;
    private final FileLookup files;
    private final EventLockService locks;
    private final VenueLookup venues;

    public PrintFormService(
            DocumentDataService data,
            PdfRenderer renderer,
            EventLookup events,
            DiplomaSettingsLookup settings,
            FileLookup files,
            EventLockService locks,
            VenueLookup venues) {
        this.data = data;
        this.renderer = renderer;
        this.events = events;
        this.settings = settings;
        this.files = files;
        this.locks = locks;
        this.venues = venues;
    }

    private PdfDocument pdf(
            UUID eventId,
            Protocol protocol,
            String name,
            String template,
            Map<String, Object> model) {
        var header = events.protocolHeader(eventId);
        String date =
                LocalDate.parse(protocol.header().date(), DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        .toString();
        return new PdfDocument(
                renderer.render(template, model),
                name
                        + "-"
                        + (header.publicSlug() == null ? eventId : header.publicSlug())
                        + "-"
                        + date
                        + ".pdf");
    }

    public PdfDocument cards(UUID eventId, UUID disciplineId, UUID sessionId, CardFormat format) {
        var protocol = data.protocol(eventId, false, true, disciplineId, sessionId);
        var cards =
                protocol.disciplines().stream()
                        .flatMap(d -> d.heats().stream().map(h -> new Card(d, h, d.heats().size())))
                        .toList();
        return pdf(
                eventId,
                protocol,
                "Карточки-заплывов",
                "heat-cards",
                Map.of("protocol", protocol, "cards", cards, "twoUp", format == CardFormat.A4x2));
    }

    public Protocol awardsData(
            UUID eventId,
            boolean publishedOnly,
            Scope scope,
            int places,
            UUID disciplineId,
            String ageGroupCode) {
        if (places < 1 || places > 500)
            throw new BusinessException(
                    "documents.invalid_places", "Число мест должно быть от 1 до 500");
        var p = data.protocol(eventId, publishedOnly, true, disciplineId, null);
        var disciplines = new ArrayList<Discipline>();
        for (var d : p.disciplines()) {
            var sections = new ArrayList<ResultSection>();
            for (var s : d.resultSections()) {
                if ((scope == Scope.ABSOLUTE) != (s.code() == null)) continue;
                if (ageGroupCode != null && !ageGroupCode.equals(s.code())) continue;
                var rows =
                        s.ranked().stream()
                                .filter(r -> r.place() != null && r.place() <= places)
                                .toList();
                if (!rows.isEmpty())
                    sections.add(new ResultSection(s.name(), rows, List.of(), s.code()));
            }
            if (!sections.isEmpty())
                disciplines.add(
                        new Discipline(
                                d.number(),
                                d.name(),
                                d.session(),
                                List.of(),
                                sections,
                                d.id(),
                                d.sessionId()));
        }
        return new Protocol(p.header(), List.copyOf(disciplines), p.showBibs());
    }

    public PdfDocument awards(
            UUID eventId, boolean publishedOnly, Scope scope, int places, UUID disciplineId) {
        var p = awardsData(eventId, publishedOnly, scope, places, disciplineId, null);
        return pdf(eventId, p, "Лист-награждения", "awards", Map.of("protocol", p));
    }

    public PdfDocument diplomas(
            UUID eventId,
            Scope scope,
            int places,
            UUID disciplineId,
            String ageGroupCode,
            boolean sample,
            UUID userId) {
        var p = awardsData(eventId, false, scope, places, disciplineId, ageGroupCode);
        var config = settings.get(eventId).normalized();
        config.validate();
        if (!sample && !config.enabled())
            throw new BusinessException(
                    "documents.diploma_disabled", "Включите грамоты в настройках события");
        var pages = new ArrayList<Diploma>();
        var common = diplomaValues(p, eventId);
        if (sample)
            pages.add(
                    diploma(
                            config,
                            p,
                            new ResultRow(
                                    1,
                                    "Иванова Мария",
                                    "2014",
                                    "Волна",
                                    "00:32.15",
                                    "VALID",
                                    false,
                                    null,
                                    "Иванова",
                                    "Мария"),
                            "50 м вольный стиль",
                            "2014–2015",
                            common));
        else {
            long count =
                    p.disciplines().stream()
                            .flatMap(d -> d.resultSections().stream())
                            .mapToLong(s -> s.ranked().size())
                            .sum();
            if (count > 500)
                throw new BusinessException(
                        "documents.too_many_pages",
                        "Более 500 грамот: используйте фильтры вида и возрастной группы");
            for (var d : p.disciplines())
                for (var s : d.resultSections())
                    for (var r : s.ranked())
                        pages.add(diploma(config, p, r, d.name(), s.name(), common));
        }
        String background = "";
        if (config.backgroundFileKey() != null) {
            try (var input = files.get(config.backgroundFileKey(), eventId, userId)) {
                var bytes = input.readAllBytes();
                String mime =
                        bytes.length > 2 && bytes[0] == (byte) 0x89 ? "image/png" : "image/jpeg";
                background =
                        "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            } catch (IOException e) {
                throw new BusinessException(
                        "files.read_failed", "Не удалось прочитать фон грамоты");
            }
        }
        return pdf(
                eventId,
                p,
                "Грамоты",
                "diplomas",
                Map.of("protocol", p, "diplomas", pages, "background", background, "logo", LOGO));
    }

    private Diploma diploma(
            DiplomaSettings c,
            Protocol p,
            ResultRow row,
            String discipline,
            String group,
            Map<String, String> common) {
        var values = new HashMap<>(common);
        values.put("lastName", row.lastName());
        values.put("firstName", row.firstName());
        values.put("birthYear", row.year());
        values.put("club", row.club());
        values.put("place", row.place().toString());
        values.put("placeText", DiplomaSettings.placeText(row.place()));
        values.put("discipline", discipline);
        values.put("ageGroup", group);
        values.put("result", row.result());
        return new Diploma(
                c.substitute(c.title(), values),
                c.substitute(c.bodyTemplate(), values),
                c.substitute(c.footer(), values),
                c.showLogo());
    }

    private Map<String, String> diplomaValues(Protocol p, UUID eventId) {
        var event = events.protocolHeader(eventId);
        var venue = event.venueId() == null ? null : venues.protocolVenue(event.venueId());
        var values = new HashMap<String, String>();
        values.put("eventTitle", p.header().title());
        String dates = p.header().date();
        if (event.endsAt() != null) {
            var zone = venue == null ? ZoneOffset.UTC : ZoneId.of(venue.timezone());
            String end =
                    event.endsAt()
                            .atZoneSameInstant(zone)
                            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            if (!end.equals(dates)) dates += " – " + end;
        }
        values.put("eventDates", dates);
        values.put("venue", venue == null ? "—" : venue.name());
        values.put("city", venue == null || venue.city() == null ? "—" : venue.city());
        return values;
    }

    @Transactional
    public Map<String, String> uploadBackground(UUID eventId, MultipartFile file, UUID userId) {
        locks.lockWritableForUpdate(eventId);
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new BusinessException(
                    "documents.invalid_background", "Фон: PNG или JPEG размером до 5 МБ");
        try {
            byte[] bytes = file.getBytes();
            String mime =
                    bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                            ? "image/png"
                            : bytes.length > 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                                    ? "image/jpeg"
                                    : null;
            if (mime == null)
                throw new BusinessException(
                        "documents.invalid_background",
                        "Фон должен быть изображением PNG или JPEG");
            try (var image = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(image);
                if (!readers.hasNext())
                    throw new BusinessException(
                            "documents.invalid_background", "Не удалось прочитать изображение");
                var reader = readers.next();
                try {
                    reader.setInput(image);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 12_000_000)
                        throw new BusinessException(
                                "documents.invalid_background",
                                "Фон должен содержать не более 12 млн пикселей");
                } finally {
                    reader.dispose();
                }
            }
            var old = settings.get(eventId).backgroundFileKey();
            var stored =
                    files.put(
                            new ByteArrayInputStream(bytes),
                            bytes.length,
                            mime.equals("image/png") ? "background.png" : "background.jpg",
                            mime,
                            eventId,
                            userId);
            settings.setBackground(eventId, stored.key());
            if (old != null) files.delete(old, eventId, userId);
            return Map.of("backgroundFileKey", stored.key());
        } catch (IOException e) {
            throw new BusinessException(
                    "documents.invalid_background", "Не удалось прочитать фон грамоты");
        }
    }
}
