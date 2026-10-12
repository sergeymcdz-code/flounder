package com.acme.sportplatform.regulations;

import java.util.UUID;

public interface RegulationVersionLookup {

    RegulationVersionLookupResult getPublishedById(UUID regulationVersionId);
}