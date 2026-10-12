package com.acme.sportplatform.competition;

import java.util.UUID;

public interface CompetitionUnitUsageLookup {
    boolean isInUse(UUID id);
}
