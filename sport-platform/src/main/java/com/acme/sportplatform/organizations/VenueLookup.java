package com.acme.sportplatform.organizations;

import java.util.UUID;

public interface VenueLookup {

    boolean existsById(UUID venueId);

    String getMetadata(UUID venueId);

    ProtocolVenue protocolVenue(UUID venueId);

    record ProtocolVenue(String name, String city, String timezone, String metadata) {}
}
