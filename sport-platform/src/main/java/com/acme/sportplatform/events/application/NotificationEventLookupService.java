package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.NotificationEventLookup;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationEventLookupService implements NotificationEventLookup {
    private final EventRepository events;
    public NotificationEventLookupService(EventRepository events) { this.events = events; }
    public Optional<Context> findNotificationContext(UUID id) {
        return events.findById(id).map(e -> new Context(e.getId(), e.getOrganizationId(),
                e.getTitle(), e.getEventStartAt(), e.getPublicSlug(), e.getVenueId()));
    }
}
