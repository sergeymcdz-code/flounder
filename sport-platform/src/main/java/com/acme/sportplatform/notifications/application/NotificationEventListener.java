package com.acme.sportplatform.notifications.application;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.NotificationEventLookup;
import com.acme.sportplatform.identity.UserLookup;
import com.acme.sportplatform.notifications.domain.NotificationTemplate;
import com.acme.sportplatform.organizations.VenueLookup;
import com.acme.sportplatform.notifications.infrastructure.*;
import com.acme.sportplatform.registrations.*;
import com.acme.sportplatform.results.ResultsPublishedEvent;
import com.acme.sportplatform.scheduling.StartListPublishedEvent;
import com.acme.sportplatform.scheduling.ScheduleChangedEvent;
import com.acme.sportplatform.results.CompetitionUnitStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationEventListener {
    private final RegistrationLookup registrations;
    private final CompetitionUnitStore units;
    private final VenueLookup venues;
    private final UserLookup users;
    private final EventDisciplineLookup disciplines;
    private final NotificationEventLookup events;
    private final NotificationSettingsRepository settings;
    private final NotificationOutboxRepository outbox;
    private final ObjectMapper json;
    private final Clock clock;
    private final String publicUrl;
    public NotificationEventListener(RegistrationLookup registrations, CompetitionUnitStore units, VenueLookup venues, UserLookup users, EventDisciplineLookup disciplines,
            NotificationEventLookup events, NotificationSettingsRepository settings, NotificationOutboxRepository outbox,
            ObjectMapper json, @Qualifier("notificationClock") Clock clock, @Value("${app.public-url:http://localhost:5173}") String publicUrl) {
        this.registrations = registrations; this.units = units; this.venues = venues; this.users = users; this.disciplines = disciplines; this.events = events;
        this.settings = settings; this.outbox = outbox; this.json = json; this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }
    // Synchronous Modulith domain events: an outbox insert rolls back with the publisher.
    // ApplicationModuleListener is AFTER_COMMIT/REQUIRES_NEW and cannot provide this guarantee.
    @EventListener
    public void submitted(RegistrationSubmittedEvent e) { registration(e.registrationId(), NotificationTemplate.registration_submitted, ""); }
    @EventListener
    public void confirmed(RegistrationConfirmedEvent e) { registration(e.registrationId(), NotificationTemplate.registration_confirmed, ""); }
    @EventListener
    public void rejected(RegistrationRejectedEvent e) { registration(e.registrationId(), NotificationTemplate.registration_rejected, e.reason()); }
    @EventListener
    public void startList(StartListPublishedEvent e) {
        var rows = e.disciplineId() == null ? registrations.findByEventId(e.eventId()) : registrations.findByDisciplineId(e.disciplineId());
        broadcast(e.eventId(), rows, NotificationTemplate.start_list_published, e.publicationId());
    }
    @EventListener
    public void results(ResultsPublishedEvent e) {
        disciplines.findById(e.disciplineId()).ifPresent(d -> broadcast(d.eventId(),
                registrations.findByIdIn(e.registrationIds()).stream()
                        .filter(r -> e.disciplineId().equals(r.eventDisciplineId())).toList(),
                NotificationTemplate.results_published, e.competitionUnitId()));
    }
    private record ScheduledRegistration(UUID disciplineId, Map<String, Object> change) {}

    @EventListener
    public void scheduleChanged(ScheduleChangedEvent e) {
        events.findNotificationContext(e.eventId()).ifPresent(event -> {
            if (!settings.enabled(event.organizationId(), NotificationTemplate.schedule_changed.name())) return;
            var changed = new HashMap<UUID, ScheduleChangedEvent.UnitChange>();
            var unitIds = Set.copyOf(e.unitIds());
            e.changes().stream().filter(c -> unitIds.contains(c.unitId())).forEach(c -> changed.put(c.unitId(), c));
            var changesByRegistration = new LinkedHashMap<UUID, List<ScheduledRegistration>>();
            var zone = ZoneId.of(event.venueId() == null ? "UTC" : venues.getTimezone(event.venueId()));
            var time = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru")).withZone(zone);
            for (var discipline : disciplines.findByEventId(e.eventId())) {
                for (var unit : units.findByDisciplineId(discipline.id())) {
                    var change = changed.get(unit.id());
                    if (change == null) continue;
                    var line = new ScheduledRegistration(discipline.id(), Map.of(
                            "discipline", discipline.name(), "number", unit.number(),
                            "before", time.format(change.previousScheduledAt()), "after", time.format(change.scheduledAt())));
                    unit.entries().stream().filter(entry -> "ASSIGNED".equals(entry.status())).forEach(entry ->
                            changesByRegistration.computeIfAbsent(entry.registrationId(), ignored -> new ArrayList<>()).add(line));
                }
            }
            if (changesByRegistration.isEmpty()) return;
            var eligible = registrations.findByIdIn(new ArrayList<>(changesByRegistration.keySet())).stream()
                    .filter(r -> e.eventId().equals(r.eventId()) && r.participantUserId() != null
                            && Set.of("CONFIRMED", "PAID", "ADMITTED").contains(r.status()))
                    .filter(r -> changesByRegistration.get(r.id()).stream().anyMatch(line -> line.disciplineId().equals(r.eventDisciplineId())))
                    .toList();
            var userIds = eligible.stream().map(RegistrationLookupResult::participantUserId).distinct().toList();
            var usersById = new HashMap<UUID, UserLookup.User>();
            if (!userIds.isEmpty()) users.findAllByIds(userIds).forEach(user -> usersById.put(user.id(), user));
            var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
            var recipients = new HashMap<String, UserLookup.User>();
            for (var registration : eligible) {
                var user = usersById.get(registration.participantUserId());
                if (user == null || user.email() == null || user.email().isBlank()) continue;
                String email = user.email().trim().toLowerCase(Locale.ROOT);
                recipients.putIfAbsent(email, user);
                String participant = participantName(registration);
                for (var line : changesByRegistration.get(registration.id())) {
                    if (!line.disciplineId().equals(registration.eventDisciplineId())) continue;
                    var change = new HashMap<>(line.change());
                    change.put("participant", participant);
                    rows.computeIfAbsent(email, ignored -> new ArrayList<>()).add(change);
                }
            }
            // Each shift is a separate delivery, including repeated shifts within ten minutes.
            String delivery = UUID.randomUUID().toString();
            rows.forEach((email, changes) -> {
                var user = recipients.get(email);
                var payload = new HashMap<String, Object>();
                payload.put("name", user.firstName() == null ? "Участник" : user.firstName());
                payload.put("event", event.title());
                payload.put("date", event.startsAt() == null ? "" : event.startsAt().atZoneSameInstant(zone)
                        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))));
                payload.put("link", publicUrl + "/e/" + event.publicSlug() + "/schedule");
                payload.put("changes", changes);
                outbox.enqueue(event.id(), email, "schedule_changed", payload,
                        "schedule_changed:" + delivery + ":" + user.id(), clock.instant());
            });
        });
    }
    private String participantName(RegistrationLookupResult registration) {
        try {
            var snapshot = json.readTree(registration.participantSnapshot());
            if (snapshot != null) {
                String name = (snapshot.path("lastName").asText("").trim() + " "
                        + snapshot.path("firstName").asText("").trim()).trim();
                if (!name.isBlank()) return name;
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ignored) { }
        return "Участник";
    }
    private void registration(UUID id, NotificationTemplate template, String reason) {
        registrations.findById(id).ifPresent(r -> events.findNotificationContext(r.eventId()).ifPresent(event -> {
            if (!settings.enabled(event.organizationId(), template.name())) return;
            var user = recipient(r);
            user.ifPresent(u -> enqueue(event, r, u, template, reason, UUID.randomUUID().toString()));
        }));
    }
    private void broadcast(UUID eventId, List<RegistrationLookupResult> rows, NotificationTemplate template, UUID publicationId) {
        events.findNotificationContext(eventId).ifPresent(event -> {
            if (!settings.enabled(event.organizationId(), template.name())) return;
            var eligible = rows.stream().filter(r -> eventId.equals(r.eventId())
                    && Set.of("CONFIRMED", "PAID", "ADMITTED").contains(r.status())).toList();
            var userIds = eligible.stream().map(RegistrationLookupResult::participantUserId)
                    .filter(Objects::nonNull).distinct().toList();
            var recipients = new HashMap<UUID, UserLookup.User>();
            if (!userIds.isEmpty()) users.findAllByIds(userIds).forEach(u -> recipients.put(u.id(), u));
            Set<String> sent = new HashSet<>();
            for (var r : eligible) {
                Optional.ofNullable(recipients.get(r.participantUserId()))
                        .filter(u -> u.email() != null && !u.email().isBlank()).ifPresent(u -> {
                    String email = u.email().toLowerCase(Locale.ROOT);
                    if (sent.add(email)) enqueue(event, r, u, template, "", publicationId + ":" + email);
                });
            }
        });
    }
    private Optional<UserLookup.User> recipient(RegistrationLookupResult r) {
        // RegistrationLookup resolves the profile's account (including an attached child's parent account).
        if (r.participantUserId() == null) return Optional.empty();
        return users.findById(r.participantUserId()).filter(u -> u.email() != null && !u.email().isBlank());
    }
    private void enqueue(NotificationEventLookup.Context event, RegistrationLookupResult registration, UserLookup.User user,
                         NotificationTemplate template, String reason, String key) {
        var payload = new HashMap<String, Object>();
        String name = user.firstName() == null ? "Участник" : user.firstName();
        try {
            var snapshot = json.readTree(registration.participantSnapshot());
            name = snapshot.path("firstName").asText(name);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ignored) { }
        payload.put("name", name); payload.put("event", event.title());
        payload.put("date", event.startsAt() == null ? "" : event.startsAt().atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))));
        payload.put("reason", reason == null ? "Причина не указана" : reason);
        String base = publicUrl + (event.publicSlug() == null ? "" : "/e/" + event.publicSlug());
        payload.put("link", base + (template == NotificationTemplate.start_list_published ? "/start-list"
                : template == NotificationTemplate.results_published ? "/results" : ""));
        payload.put("registrationId", registration.id().toString());
        outbox.enqueue(event.id(), user.email(), template.name(), payload, template.name() + ":" + key, clock.instant());
    }
}
