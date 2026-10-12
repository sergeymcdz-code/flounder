package com.acme.sportplatform.events;

import java.util.UUID;

public interface SessionUsageLookup {
    boolean isInUse(UUID id);
}
