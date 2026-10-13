package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventLifecycleScheduler {
    private final EventRepository events;
    private final EventLifecycleService lifecycle;

    public EventLifecycleScheduler(EventRepository events, EventLifecycleService lifecycle) {
        this.events = events;
        this.lifecycle = lifecycle;
    }

    @Scheduled(cron = "${events.lifecycle.cron:0 * * * * *}")
    public void advanceDueEvents() {
        for (var event : events.findByStatusInOrderByEventStartAtAsc(List.of("PUBLISHED", "REGISTRATION_OPEN"))) {
            try { lifecycle.advanceDue(event.getId()); }
            catch (RuntimeException exception) {
                LoggerFactory.getLogger(getClass()).error("Event {} automatic transition failed", event.getId(), exception);
            }
        }
    }
}
