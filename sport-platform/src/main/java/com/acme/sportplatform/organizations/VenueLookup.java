package com.acme.sportplatform.organizations;

import java.util.UUID;

public interface VenueLookup {

    boolean existsById(UUID venueId);

    java.util.Map<UUID,String> cities(java.util.Collection<UUID> ids);

    String getMetadata(UUID venueId);

    ProtocolVenue protocolVenue(UUID venueId);

    String getTimezone(UUID venueId);

    record ProtocolVenue(String name, String city, String timezone, String metadata) {}
}
