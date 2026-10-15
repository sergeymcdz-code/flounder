package com.acme.sportplatform.standings;

import java.util.UUID;

public interface StandingsLookup {
    StandingsView forEvent(UUID eventId, boolean details);
    StandingsView publicBySlug(String slug);
}
