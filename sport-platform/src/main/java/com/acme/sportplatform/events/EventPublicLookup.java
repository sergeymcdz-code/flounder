package com.acme.sportplatform.events;

import java.util.List;

public interface EventPublicLookup {

    List<EventPublicLookupResult> listPublicEvents();

    EventPublicLookupResult getPublicBySlug(
            String publicSlug
    );
}