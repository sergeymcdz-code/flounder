package com.acme.sportplatform.standings;

import com.acme.sportplatform.events.EventPublicLookupResult;
import java.util.UUID;

public interface StandingsLookup {
    StandingsView forEvent(UUID eventId, boolean details);

    /** Score the caller's bounded export snapshot without loading another copy of all athletes. */
    StandingsView forExport(UUID eventId, java.util.List<com.acme.sportplatform.results.ResultsProtocolLookup.Heat> heats,
            java.util.List<com.acme.sportplatform.competition.EventDisciplineSummary> program);
    StandingsView publicBySlug(String slug);

    /** Caller must obtain the event through EventPublicLookup.getPublicBySlug to enforce visibility. */
    StandingsView forPublicEvent(EventPublicLookupResult event);
}
