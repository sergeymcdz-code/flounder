package com.acme.sportplatform.organizations;

import java.util.UUID;

public interface VenueLookup {

    boolean existsById(UUID venueId);
}