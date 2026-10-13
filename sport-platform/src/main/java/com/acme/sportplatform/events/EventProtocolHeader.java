package com.acme.sportplatform.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventProtocolHeader(UUID id, String title, UUID organizationId, UUID venueId,
        OffsetDateTime startsAt, OffsetDateTime endsAt, String publicSlug) {}
