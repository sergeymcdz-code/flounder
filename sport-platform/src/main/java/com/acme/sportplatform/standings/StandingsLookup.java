package com.acme.sportplatform.standings;

import com.acme.sportplatform.events.EventPublicLookupResult;
import java.util.UUID;

public interface StandingsLookup {
    StandingsView forEvent(UUID eventId, boolean details);
    StandingsView publicBySlug(String slug);

    /** Caller must obtain the event through EventPublicLookup.getPublicBySlug to enforce visibility. */
    StandingsView forPublicEvent(EventPublicLookupResult event);
}
