package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.NotificationEventLookup;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationEventLookupService implements NotificationEventLookup {
    private final com.acme.sportplatform.organizations.VenueLookup venues;
    private final EventRepository events;

    public NotificationEventLookupService(
            EventRepository events, com.acme.sportplatform.organizations.VenueLookup venues) {
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
                                        timezone(e.getVenueId())));
    }

    public java.util.List<UUID> paymentReminderEventIds(java.time.Instant now) {
        return events
                .findByStatusInOrderByEventStartAtAsc(
                        java.util.Set.of(
                                "DRAFT",
                                "PUBLISHED",
                                "REGISTRATION_OPEN",
                                "REGISTRATION_CLOSED",
                                "LIVE",
                                "COMPLETED"))
                .stream()
                .filter(
                        e -> {
                            var zone = java.time.ZoneId.of(timezone(e.getVenueId()));
                            var local = now.atZone(zone);
                            return local.getHour() == 9 && local.getMinute() == 0;
                        })
                .map(e -> e.getId())
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
