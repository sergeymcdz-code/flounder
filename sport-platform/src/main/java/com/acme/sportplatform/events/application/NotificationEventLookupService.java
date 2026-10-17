package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.NotificationEventLookup;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.events.infrastructure.persistence.repository.NotificationEventReadRepository;
import com.acme.sportplatform.organizations.VenueLookup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationEventLookupService implements NotificationEventLookup {
    private final VenueLookup venues;
    private final EventRepository events;
    private final NotificationEventReadRepository reminders;

    public NotificationEventLookupService(
            EventRepository events,
            VenueLookup venues,
            NotificationEventReadRepository reminders) {
        this.reminders = reminders;
        this.venues = venues;
        this.events = events;
    }

    public Optional<Context> findNotificationContext(UUID id) {
        return events.findById(id)
                .map(
                        e ->
                                new Context(
                                        e.getId(),
                                        e.getOrganizationId(),
                                        e.getTitle(),
                                        e.getEventStartAt(),
                                        e.getPublicSlug(),
                                        e.getVenueId(),
                                        timezone(e.getVenueId())));
    }

    public java.util.List<Context> paymentReminderEvents(java.time.Instant now) {
        return reminders.activeContexts().stream()
                .filter(
                        e -> {
                            var local = now.atZone(java.time.ZoneId.of(e.timezone()));
                            return local.getHour() == 9 && local.getMinute() < 10;
                        })
                .toList();
    }

    private String timezone(UUID venueId) {
        if (venueId == null) return "UTC";
        try {
            String timezone = venues.getTimezone(venueId);
            return timezone == null ? "UTC" : timezone;
        } catch (com.acme.sportplatform.common.exception.BusinessException exception) {
            if ("organizations.venue_not_found".equals(exception.getCode())) return "UTC";
            throw exception;
        }
    }
}
