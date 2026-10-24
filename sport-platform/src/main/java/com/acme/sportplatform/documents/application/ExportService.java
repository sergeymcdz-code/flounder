package com.acme.sportplatform.documents.application;

import com.acme.sportplatform.common.TimeParser;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.documents.application.export.ExportWriter;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventPublicLookup;
import com.acme.sportplatform.results.ResultsProtocolLookup;
import com.acme.sportplatform.results.ResultsProtocolLookup.*;
import com.acme.sportplatform.results.ResultsPublicationLookup;
import com.acme.sportplatform.standings.StandingsLookup;
import com.acme.sportplatform.standings.StandingsView;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {
    public static final int MAX_ROWS = 100_000;
    public enum Kind {
        RESULTS("Результаты"), STANDINGS("Командный зачёт"), START_LIST("Стартовый протокол");
        final String title;
        Kind(String title) { this.title = title; }
    }

    /** A bounded data snapshot, never encoded CSV/XLSX bytes or a complete table of export cells. */
    public record Plan(Kind kind, String eventTitle, List<EventDisciplineSummary> program,
            Map<UUID, String> sessions, List<Heat> heats, StandingsView standings,
            Map<UUID, Boolean> officialByDiscipline, boolean publishedOnly) {
        public boolean official() {
            return !officialByDiscipline.isEmpty()
                    && officialByDiscipline.values().stream().allMatch(Boolean::booleanValue);
        }
    }

    private final EventLookup events;
    private final EventPublicLookup publicEvents;
    private final EventDisciplineLookup disciplines;
    private final ResultsProtocolLookup results;
    private final ResultsPublicationLookup publications;
    private final StandingsLookup standings;

    public ExportService(EventLookup events, EventPublicLookup publicEvents, EventDisciplineLookup disciplines,
            ResultsProtocolLookup results, ResultsPublicationLookup publications, StandingsLookup standings) {
        this.events = events;
        this.publicEvents = publicEvents;
        this.disciplines = disciplines;
        this.results = results;
        this.publications = publications;
        this.standings = standings;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Plan prepare(UUID eventId, Kind kind) {
        return snapshot(eventId, kind, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Plan publicResults(String slug) {
        var event = publicEvents.getPublicBySlug(slug);
        return snapshot(event.id(), Kind.RESULTS, true);
    }

    private Plan snapshot(UUID eventId, Kind kind, boolean publishedOnly) {
        var event = events.getById(eventId);
        var sessionOrder = event.sessions().stream().collect(Collectors.toMap(s -> s.id(), s -> s.orderNo()));
        var sessionNames = event.sessions().stream().collect(Collectors.toMap(s -> s.id(), s -> s.name()));
        var program = new ArrayList<>(publishedOnly
                ? disciplines.findPublishedByEventId(eventId) : disciplines.findByEventId(eventId));
        program.sort(Comparator.comparing((EventDisciplineSummary d) -> sessionOrder.get(d.sessionId()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EventDisciplineSummary::orderInSession, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EventDisciplineSummary::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(d -> d.id().toString()));
        var ids = program.stream().map(EventDisciplineSummary::id).toList();
        if (publications.countExportEntries(ids, publishedOnly) > MAX_ROWS) throw tooLarge();
        var heats = results.heats(ids, publishedOnly);
        if (heats.stream().mapToLong(h -> h.entries().size()).sum() > MAX_ROWS) throw tooLarge();
        if (publishedOnly && heats.stream().flatMap(h -> h.entries().stream()).noneMatch(ExportService::hasResult)) {
            throw new BusinessException("results.public_not_found", "Опубликованные результаты не найдены");
        }
        var scoring = kind == Kind.START_LIST ? null : standings.forExport(eventId, heats, program);
        return new Plan(kind, events.protocolHeader(eventId).title(), List.copyOf(program), Map.copyOf(sessionNames), List.copyOf(heats), scoring,
                publications.officialByDiscipline(ids), publishedOnly);
    }

    public void write(Plan plan, boolean xlsx, OutputStream output) throws IOException {
        try (var writer = xlsx ? ExportWriter.xlsx(output, plan.kind().title, plan.official()) : ExportWriter.csv(output)) {
            writer.row(List.of(plan.eventTitle(), plan.kind().title));
            writer.row(List.of(plan.official() ? "Официальный протокол" : "Предварительно", "official", plan.official()));
            switch (plan.kind()) {
                case RESULTS -> results(plan, writer);
                case START_LIST -> startList(plan, writer);
                case STANDINGS -> standings(plan, writer);
            }
        }
    }

    private record ScoreKey(UUID disciplineId, UUID profileId) {}
    private record Row(Heat heat, Entry entry) {}

    private void results(Plan plan, ExportWriter writer) throws IOException {
        writer.row(List.of("Вид программы", "Место", "Участник", "Год", "Клуб", "Время/результат", "Статус", "Очки", "official"));
        Map<ScoreKey, Integer> scores = new HashMap<>();
        for (var club : plan.standings().team()) {
            if (club.breakdown() == null) continue;
            for (var score : club.breakdown()) {
                if (score.profileId() != null) scores.put(new ScoreKey(score.disciplineId(), score.profileId()), score.points());
            }
        }
        var heats = plan.heats().stream().collect(Collectors.groupingBy(Heat::disciplineId));
        for (var discipline : plan.program()) {
            var ordered = heats.getOrDefault(discipline.id(), List.of()).stream()
                    .flatMap(h -> h.entries().stream().filter(e -> !plan.publishedOnly() || hasResult(e)).map(e -> new Row(h, e)))
                    .sorted(Comparator.comparing((Row r) -> place(r.entry()), Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(r -> r.entry().outcome() == null ? null : r.entry().outcome().timeMs(),
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(r -> name(r.entry()))).toList();
            for (var row : ordered) {
                var e = row.entry();
                writer.row(Arrays.asList(discipline.name(), place(e), name(e), e.birthYear(), e.club(), value(e), status(e),
                        scores.getOrDefault(new ScoreKey(discipline.id(), e.profileId()), 0),
                        plan.officialByDiscipline().getOrDefault(discipline.id(), false)));
            }
        }
    }

    private void startList(Plan plan, ExportWriter writer) throws IOException {
        writer.row(List.of("Сессия", "Вид программы", "Заплыв", "Плановое время", "Группа", "Дорожка", "Участник", "Год", "Клуб", "Заявочное время", "Статус", "official"));
        var heats = plan.heats().stream().collect(Collectors.groupingBy(Heat::disciplineId));
        for (var discipline : plan.program()) {
            for (var heat : heats.getOrDefault(discipline.id(), List.of()).stream().sorted(Comparator.comparingInt(Heat::number)).toList()) {
                for (var e : heat.entries()) {
                    writer.row(Arrays.asList(discipline.sessionId() == null ? "Без сессии" : plan.sessions().getOrDefault(discipline.sessionId(), "Без сессии"), discipline.name(),
                            heat.number(), heat.scheduledAt(), heat.ageGroupCode(), e.lane(), name(e), e.birthYear(), e.club(),
                            e.seedTimeMs() == null ? "NT" : TimeParser.format(e.seedTimeMs()), e.withdrawn() ? "Снят" : "",
                            plan.officialByDiscipline().getOrDefault(discipline.id(), false)));
                }
            }
        }
    }

    private void standings(Plan plan, ExportWriter writer) throws IOException {
        writer.row(List.of("Место", "Клуб", "Очки", "Золото", "Серебро", "Бронза", "Зачтено результатов", "official"));
        int rank = 0;
        for (var club : plan.standings().team()) {
            writer.row(List.of(++rank, club.club(), club.points(), club.gold(), club.silver(), club.bronze(),
                    club.scoredResults(), plan.official() && !plan.standings().preliminary()));
        }
    }

    private static boolean hasResult(Entry e) { return e.outcome() != null && !"PENDING".equals(e.outcome().status()); }
    private static Integer place(Entry e) { return "VALID".equals(status(e)) ? e.outcome().place() : null; }
    private static String name(Entry e) { return Objects.toString(e.lastName(), "") + " " + Objects.toString(e.firstName(), ""); }
    private static String value(Entry e) {
        if (e.outcome() == null || "NT".equals(status(e))) return "NT";
        if (!"VALID".equals(status(e))) return status(e);
        return e.outcome().timeMs() != null ? TimeParser.format(e.outcome().timeMs()) : Objects.toString(e.outcome().rawValue(), "NT");
    }
    private static String status(Entry e) {
        if (!hasResult(e)) return "NT";
        String status = e.outcome().status();
        if ("CORRECTED".equals(status)) status = e.outcome().timeMs() == null ? e.outcome().rawValue() : "VALID";
        return switch (Objects.toString(status, "")) {
            case "DID_NOT_START", "DNS" -> "DNS";
            case "DID_NOT_FINISH", "DNF" -> "DNF";
            case "DISQUALIFIED", "DSQ" -> "DSQ";
            case "VALID", "OK" -> "VALID";
            default -> "NT";
        };
    }
    private static BusinessException tooLarge() {
        return new BusinessException("documents.export_too_large", "Экспорт ограничен 100 000 строками; уменьшите программу события");
    }
}
