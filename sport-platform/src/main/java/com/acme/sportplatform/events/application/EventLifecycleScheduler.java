package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventLifecycleScheduler {
    private final Clock clock;
    private final EventRepository events;
    private final EventLifecycleService lifecycle;

    public EventLifecycleScheduler(EventRepository events, EventLifecycleService lifecycle,
            @Qualifier("eventClock") Clock clock) {
        this.clock = clock;
        this.events = events;
        this.lifecycle = lifecycle;
    }

    @Scheduled(cron = "${events.lifecycle.cron:0 * * * * *}")
    public void advanceDueEvents() {
        int processed = 0;
        for (var event : events.findDueLifecycleEvents(OffsetDateTime.now(clock))) {
            try { lifecycle.advanceDue(event.getId()); processed++; }
            catch (RuntimeException exception) {
                LoggerFactory.getLogger(getClass()).error("Event {} automatic transition failed", event.getId(), exception);
            }
        }
        LoggerFactory.getLogger(getClass()).debug("Lifecycle processed {} events", processed);
    }
}
