package com.acme.sportplatform.events.application;

import com.acme.sportplatform.events.api.EventResponse;
import com.acme.sportplatform.events.domain.EventStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CloseEventRegistrationUseCase {
    private final EventLifecycleService lifecycle;
    public CloseEventRegistrationUseCase(EventLifecycleService lifecycle) { this.lifecycle = lifecycle; }
    public EventResponse execute(UUID eventId) { return lifecycle.transition(eventId, EventStatus.REGISTRATION_CLOSED); }
}
