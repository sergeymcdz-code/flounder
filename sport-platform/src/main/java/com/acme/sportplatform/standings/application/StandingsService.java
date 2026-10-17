package com.acme.sportplatform.standings.application;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventPublicLookup;
import com.acme.sportplatform.events.EventPublicLookupResult;
import com.acme.sportplatform.results.ResultsProtocolLookup;
import com.acme.sportplatform.standings.StandingsLookup;
import com.acme.sportplatform.standings.StandingsPublishedEvent;
import com.acme.sportplatform.standings.StandingsView;
import com.acme.sportplatform.standings.StandingsView.ClubPoints;
import com.acme.sportplatform.standings.api.StandingsConfigRequest;
import com.acme.sportplatform.standings.domain.StandingsCalculator;
import com.acme.sportplatform.standings.domain.StandingsConfig;
import com.acme.sportplatform.standings.infrastructure.StandingsConfigRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StandingsService implements StandingsLookup {
    private final StandingsConfigRepository configs;
    private final EventLookup events;
    private final EventPublicLookup publicEvents;
    private final EventDisciplineLookup disciplines;
    private final ResultsProtocolLookup results;
    private final ApplicationEventPublisher publisher;
    private final StandingsCalculator calculator = new StandingsCalculator(Clock.systemUTC());
    private final Cache<String, StandingsView> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(256)
            .build();

    public StandingsService(StandingsConfigRepository configs, EventLookup events, EventPublicLookup publicEvents,
            EventDisciplineLookup disciplines, ResultsProtocolLookup results, ApplicationEventPublisher publisher) {
        this.configs = configs;
        this.events = events;
        this.publicEvents = publicEvents;
        this.disciplines = disciplines;
        this.results = results;
        this.publisher = publisher;
    }

    public StandingsConfig config(UUID eventId) {
        events.getById(eventId);
        return configs.get(eventId);
    }

    @Transactional
    public StandingsConfig save(UUID eventId, StandingsConfigRequest request) {
        events.lockById(eventId);
        var before = configs.get(eventId);
        var points = request.pointsByPlace();
        if (points == null || !points.isArray() || points.isEmpty() || points.size() > 32) {
            throw invalidPoints();
        }
        List<Integer> table = new ArrayList<>();
        int previous = Integer.MAX_VALUE;
        for (var point : points) {
            if (!point.isIntegralNumber() || !point.canConvertToInt()
                    || point.intValue() < 0 || point.intValue() > previous) {
                throw invalidPoints();
            }
            previous = point.intValue();
            table.add(previous);
        }
        StandingsConfig.Scope scope;
        try {
            scope = StandingsConfig.Scope.valueOf(request.scope());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("standings.invalid_scope", "Выберите область зачёта: по группам или в абсолюте");
        }
        if (request.maxScoredPerClubPerDiscipline() != null && request.maxScoredPerClubPerDiscipline() < 1) {
            throw new BusinessException("standings.invalid_limit", "Лимит результатов клуба должен быть положительным");
        }
        if (request.title() != null && request.title().length() > 255) {
            throw new BusinessException("standings.invalid_title", "Заголовок не должен превышать 255 символов");
        }
        var allowed = disciplines.findByEventId(eventId).stream().map(d -> d.id()).toList();
        if (request.disciplineIds() != null
                && request.disciplineIds().stream().anyMatch(id -> id == null || !allowed.contains(id))) {
            throw new BusinessException("standings.invalid_disciplines", "Выбранный вид не принадлежит событию");
        }
        var config = new StandingsConfig(request.enabled(), scope, table, request.maxScoredPerClubPerDiscipline(),
                request.disciplineIds() == null ? null : request.disciplineIds().stream().distinct().sorted().toList(),
                request.published(), request.title());
        configs.save(eventId, config);
        if (!before.published() && config.published()) {
            publisher.publishEvent(new StandingsPublishedEvent(eventId));
        }
        return config;
    }

    @Override
    public StandingsView forEvent(UUID eventId, boolean details) {
        var config = config(eventId);
        return calculate(eventId, config, false, details);
    }

    @Override
    public StandingsView publicBySlug(String slug) {
        EventPublicLookupResult event;
        try {
            event = publicEvents.getPublicBySlug(slug);
        } catch (BusinessException ex) {
            if ("events.public_not_found".equals(ex.getCode())) {
                throw notPublished();
            }
            throw ex;
        }
        return forPublicEvent(event);
    }

    @Override
    public StandingsView forPublicEvent(EventPublicLookupResult event) {
        var config = configs.get(event.id());
        if (!config.enabled() || !config.published()) {
            throw notPublished();
        }
        // A changed configuration must not reuse a calculation made with older scoring rules.
        return cache.get(event.publicSlug() + ":" + event.id() + ":" + config,
                key -> calculate(event.id(), config, true, false));
    }

    private StandingsView calculate(UUID eventId, StandingsConfig config, boolean publishedOnly, boolean details) {
        var program = publishedOnly ? disciplines.findPublishedByEventId(eventId) : disciplines.findByEventId(eventId);
        var ids = program.stream()
                .map(d -> d.id())
                .filter(id -> config.disciplineIds() == null || config.disciplineIds().contains(id))
                .toList();
        var calculated = calculator.calculate(config, results.heats(ids), program);
        var team = details ? calculated.team() : calculated.team().stream()
                .map(c -> new ClubPoints(c.club(), c.points(), c.gold(), c.silver(), c.bronze(), c.scoredResults(), null))
                .toList();
        return new StandingsView(config.title(), config.scope().name(), calculated.medals(), team,
                calculated.unitsPending(), calculated.disciplinesIncluded(), calculated.calculatedAt(),
                calculated.preliminary());
    }

    private static BusinessException invalidPoints() {
        return new BusinessException("standings.invalid_points",
                "Очки должны быть невозрастающим массивом из 1–32 целых неотрицательных чисел");
    }

    private static BusinessException notPublished() {
        return new BusinessException("standings.not_published", "Зачёт не опубликован");
    }
}
