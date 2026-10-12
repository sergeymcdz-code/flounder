package com.acme.sportplatform.competition;

import java.util.UUID;

public interface RegistrationUsageLookup {
    boolean isInUse(UUID id);
}
